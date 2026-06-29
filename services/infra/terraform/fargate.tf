# ─── Fargate Pod Execution Role ───────────────────────────────────────────────
resource "aws_iam_role" "fargate_pod" {
  name = "${local.name_prefix}-fargate-pod-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks-fargate-pods.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        ArnLike = {
          "aws:SourceArn" = "arn:aws:eks:${var.aws_region}:${data.aws_caller_identity.current.account_id}:fargateprofile/${aws_eks_cluster.main.name}/*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "fargate_pod" {
  role       = aws_iam_role.fargate_pod.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSFargatePodExecutionRolePolicy"
}

# ─── Fargate Profile (asms-prod namespace) ────────────────────────────────────
resource "aws_eks_fargate_profile" "asms_prod" {
  cluster_name           = aws_eks_cluster.main.name
  fargate_profile_name   = "asms-prod-fargate"
  pod_execution_role_arn = aws_iam_role.fargate_pod.arn
  subnet_ids             = aws_subnet.private[*].id

  selector {
    namespace = "asms-prod"
  }

  selector {
    namespace = "kube-system"
  }

  depends_on = [
    aws_iam_role_policy_attachment.fargate_pod,
    aws_nat_gateway.main,
  ]
}
