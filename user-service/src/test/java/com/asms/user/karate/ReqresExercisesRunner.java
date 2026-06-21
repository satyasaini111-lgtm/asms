package com.asms.user.karate;

import com.intuit.karate.junit5.Karate;

class ReqresExercisesRunner {

    @Karate.Test
    Karate testReqresExercises() {
        return Karate.run("classpath:karate/reqres-exercises.feature").relativeTo(getClass());
    }
}
