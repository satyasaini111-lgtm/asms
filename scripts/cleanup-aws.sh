#!/usr/bin/env bash
# =============================================================================
# ASMS AWS Cleanup Script
# Deletes all AWS resources to stop billing after an ECA demo.
#
# Usage:
#   ./scripts/cleanup-aws.sh [OPTIONS]
#
# Options:
#   --region        AWS region (default: us-east-1)
#   --cluster       EKS cluster name (default: asms-prod-eks)
#   --namespace     K8s namespace (default: asms-prod)
#   --delete-ecr    Also delete all ECR repositories and images
#   --delete-state  Also delete S3 state bucket + DynamoDB lock table
#   --dry-run       Print what would be deleted without doing it
#   --yes           Skip confirmation prompt
#
# Order matters:
#   1. Delete K8s Ingress  → triggers ALB deletion by LBC
#   2. Wait for ALB gone   → unblocks VPC deletion
#   3. Delete K8s namespace → removes all pods, services, PVCs
#   4. terraform destroy   → EKS cluster, VPC, IAM roles, subnets
#   5. ECR repos           → optional (images cost ~$0.10/GB/month)
#   6. CloudWatch logs     → EKS audit/API/authenticator log groups
#   7. S3 + DynamoDB       → optional (Terraform state backend)
# =============================================================================
set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
REGION="us-east-1"
CLUSTER="asms-prod-eks"
NAMESPACE="asms-prod"
DELETE_ECR=false
DELETE_STATE=false
DRY_RUN=false
AUTO_YES=false
TF_DIR="$(dirname "$0")/../infra/terraform"

# ── Arg parsing ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --region)       REGION="$2";    shift 2 ;;
    --cluster)      CLUSTER="$2";   shift 2 ;;
    --namespace)    NAMESPACE="$2"; shift 2 ;;
    --delete-ecr)   DELETE_ECR=true;   shift ;;
    --delete-state) DELETE_STATE=true; shift ;;
    --dry-run)      DRY_RUN=true;      shift ;;
    --yes)          AUTO_YES=true;     shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# ── Helpers ───────────────────────────────────────────────────────────────────
log()  { echo "[$(date '+%H:%M:%S')] $*"; }
warn() { echo "[$(date '+%H:%M:%S')] WARN: $*" >&2; }
run()  {
  if $DRY_RUN; then
    echo "  DRY-RUN: $*"
  else
    eval "$*"
  fi
}

# ── Confirmation ──────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           ASMS AWS CLEANUP — THIS CANNOT BE UNDONE          ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Region    : $REGION"
echo "║  Cluster   : $CLUSTER"
echo "║  Namespace : $NAMESPACE"
echo "║  Delete ECR: $DELETE_ECR"
echo "║  Delete S3 : $DELETE_STATE"
echo "║  Dry run   : $DRY_RUN"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

if ! $AUTO_YES && ! $DRY_RUN; then
  read -rp "Type 'yes' to proceed: " CONFIRM
  [[ "$CONFIRM" != "yes" ]] && echo "Aborted." && exit 0
fi

# ── Step 0: Verify AWS credentials ────────────────────────────────────────────
log "Verifying AWS credentials..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "$REGION")
log "Account: $ACCOUNT_ID  Region: $REGION"

# ── Step 1: Configure kubectl ─────────────────────────────────────────────────
log "Configuring kubectl for cluster $CLUSTER..."
if aws eks describe-cluster --name "$CLUSTER" --region "$REGION" &>/dev/null; then
  run "aws eks update-kubeconfig --region $REGION --name $CLUSTER"
  CLUSTER_EXISTS=true
else
  warn "Cluster $CLUSTER not found — skipping K8s cleanup steps"
  CLUSTER_EXISTS=false
fi

# ── Step 2: Delete Ingress first (triggers ALB deletion by LBC) ───────────────
if $CLUSTER_EXISTS; then
  log "Deleting Ingress and IngressClass to trigger ALB deletion..."
  run "kubectl delete ingress asms-ingress -n $NAMESPACE --ignore-not-found=true"
  run "kubectl delete ingressclass alb --ignore-not-found=true"

  log "Waiting for ALB to be deleted (up to 5 min)..."
  if ! $DRY_RUN; then
    for i in $(seq 1 20); do
      ALB_COUNT=$(aws elbv2 describe-load-balancers --region "$REGION" \
        --query "LoadBalancers[?contains(LoadBalancerName,'k8s-asmsprod')].LoadBalancerArn" \
        --output text 2>/dev/null | wc -w || echo 0)
      if [ "$ALB_COUNT" -eq 0 ]; then
        log "ALB deleted."
        break
      fi
      log "ALB still exists, waiting... ($i/20)"
      sleep 15
    done

    # Force-delete ALB if LBC didn't clean up in time
    LEFTOVER_ALBS=$(aws elbv2 describe-load-balancers --region "$REGION" \
      --query "LoadBalancers[?contains(LoadBalancerName,'k8s-asmsprod')].LoadBalancerArn" \
      --output text 2>/dev/null || true)
    if [ -n "$LEFTOVER_ALBS" ]; then
      warn "ALB not deleted by LBC — force-deleting..."
      for ARN in $LEFTOVER_ALBS; do
        log "  Deleting ALB: $ARN"
        aws elbv2 delete-load-balancer --load-balancer-arn "$ARN" --region "$REGION"
      done
      sleep 30
    fi
  fi
fi

# ── Step 3: Delete all target groups left by LBC ──────────────────────────────
if ! $DRY_RUN; then
  log "Cleaning up orphaned ALB target groups..."
  TGS=$(aws elbv2 describe-target-groups --region "$REGION" \
    --query "TargetGroups[?contains(TargetGroupName,'k8s-asms')].TargetGroupArn" \
    --output text 2>/dev/null || true)
  for TG in $TGS; do
    log "  Deleting target group: $TG"
    aws elbv2 delete-target-group --target-group-arn "$TG" --region "$REGION" 2>/dev/null || true
  done
fi

# ── Step 4: Delete K8s namespace (removes all pods, services, PVCs) ───────────
if $CLUSTER_EXISTS; then
  log "Deleting namespace $NAMESPACE (all pods, services, configmaps, secrets)..."
  run "kubectl delete namespace $NAMESPACE --ignore-not-found=true --timeout=120s" || \
    warn "Namespace deletion timed out — Terraform destroy will clean the cluster anyway"
fi

# ── Step 5: Terraform destroy ─────────────────────────────────────────────────
log "Running terraform destroy (EKS cluster, VPC, IAM roles, subnets)..."
if [ -d "$TF_DIR" ]; then
  run "cd $TF_DIR && terraform init -input=false -reconfigure"
  run "cd $TF_DIR && terraform destroy -auto-approve -input=false"
else
  warn "Terraform dir not found at $TF_DIR — skipping"
fi

# ── Step 6: Delete LBC-created security groups (orphaned after EKS delete) ────
if ! $DRY_RUN; then
  log "Cleaning up orphaned LBC security groups..."
  SG_IDS=$(aws ec2 describe-security-groups --region "$REGION" \
    --filters "Name=tag:elbv2.k8s.aws/cluster,Values=$CLUSTER" \
    --query "SecurityGroups[].GroupId" --output text 2>/dev/null || true)
  for SG in $SG_IDS; do
    log "  Deleting security group: $SG"
    aws ec2 delete-security-group --group-id "$SG" --region "$REGION" 2>/dev/null || \
      warn "Could not delete $SG (may still be in use — retry manually)"
  done
fi

# ── Step 7: Delete CloudWatch log groups created by EKS ───────────────────────
log "Deleting EKS CloudWatch log groups..."
for LOG_GROUP in \
  "/aws/eks/$CLUSTER/cluster" \
  "/aws/containerinsights/$CLUSTER/application" \
  "/aws/containerinsights/$CLUSTER/dataplane"; do
  if aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" \
      --region "$REGION" --query 'logGroups[0].logGroupName' --output text 2>/dev/null | grep -q "$LOG_GROUP"; then
    log "  Deleting log group: $LOG_GROUP"
    run "aws logs delete-log-group --log-group-name '$LOG_GROUP' --region $REGION"
  fi
done

# ── Step 8: Delete ECR repos (optional) ───────────────────────────────────────
if $DELETE_ECR; then
  log "Deleting ECR repositories..."
  REPOS=$(aws ecr describe-repositories --region "$REGION" \
    --query "repositories[?starts_with(repositoryName,'asms/')].repositoryName" \
    --output text 2>/dev/null || true)
  for REPO in $REPOS; do
    log "  Deleting ECR repo: $REPO"
    run "aws ecr delete-repository --repository-name '$REPO' --region $REGION --force"
  done
else
  log "Skipping ECR deletion (pass --delete-ecr to remove images too)"
  log "ECR storage cost: ~\$0.10/GB/month — images are cheap to keep"
fi

# ── Step 9: Delete Terraform state backend (optional, irreversible) ────────────
if $DELETE_STATE; then
  log "Deleting Terraform state backend..."
  warn "This removes all Terraform state — infrastructure cannot be tracked after this"

  # Empty and delete S3 bucket
  BUCKET="asms-terraform-state-prod"
  if aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
    log "  Emptying S3 bucket: $BUCKET"
    run "aws s3 rm s3://$BUCKET --recursive --region $REGION"
    log "  Deleting versioned objects..."
    if ! $DRY_RUN; then
      aws s3api list-object-versions --bucket "$BUCKET" --region "$REGION" \
        --query 'Versions[].{Key:Key,VersionId:VersionId}' --output json 2>/dev/null | \
      python3 -c "
import sys, json, subprocess
objs = json.load(sys.stdin)
for o in objs:
    subprocess.run(['aws','s3api','delete-object','--bucket','$BUCKET',
                    '--key',o['Key'],'--version-id',o['VersionId'],
                    '--region','$REGION'], capture_output=True)
print(f'Deleted {len(objs)} versioned objects')
" 2>/dev/null || true
    fi
    run "aws s3api delete-bucket --bucket $BUCKET --region $REGION"
  fi

  # Delete DynamoDB lock table
  TABLE="asms-terraform-locks"
  if aws dynamodb describe-table --table-name "$TABLE" --region "$REGION" &>/dev/null; then
    log "  Deleting DynamoDB table: $TABLE"
    run "aws dynamodb delete-table --table-name $TABLE --region $REGION"
  fi
else
  log "Skipping Terraform state backend deletion (pass --delete-state to remove)"
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    CLEANUP COMPLETE                          ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Deleted:                                                    ║"
echo "║  ✓ ALB and target groups                                     ║"
echo "║  ✓ EKS cluster ($CLUSTER)                             ║"
echo "║  ✓ VPC, subnets, security groups (via Terraform)            ║"
echo "║  ✓ IAM roles and OIDC provider (via Terraform)              ║"
echo "║  ✓ CloudWatch log groups                                     ║"
if $DELETE_ECR;   then echo "║  ✓ ECR repositories and images                               ║"; fi
if $DELETE_STATE; then echo "║  ✓ S3 state bucket and DynamoDB lock table                   ║"; fi
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Still running (if any):                                     ║"
echo "║  • Jenkins (local — stop manually if needed)                 ║"
if ! $DELETE_ECR;   then echo "║  • ECR images (cheap — run with --delete-ecr to remove)     ║"; fi
if ! $DELETE_STATE; then echo "║  • S3/DynamoDB state backend (run with --delete-state)       ║"; fi
echo "╚══════════════════════════════════════════════════════════════╝"
