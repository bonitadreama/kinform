plugins { java }

dependencies { implementation("org.json:json:20240303") }

sourceSets {
    main {
        java.setSrcDirs(listOf("../app/src/main/java", "../tests"))
        java.include("org/kinform/app/CharacterBook.java", "org/kinform/app/CharacterBackup.java",
            "org/kinform/app/LiveProtocol.java", "org/kinform/app/MemoryTrace.java",
            "org/kinform/app/SuggestionReview.java", "org/kinform/app/ConversationHistory.java",
            "org/kinform/app/SessionDuration.java", "org/kinform/app/SessionTimer.java", "SessionTimerTest.java",
            "CharacterBookTest.java", "CharacterBackupTest.java", "LiveProtocolTest.java")
    }
}

val regressionTasks = listOf("CharacterBookTest", "CharacterBackupTest", "LiveProtocolTest", "SessionTimerTest").map { suite ->
    tasks.register<JavaExec>(suite) {
        group = "verification"
        dependsOn(tasks.classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("org.kinform.app.$suite")
    }
}
tasks.check { dependsOn(regressionTasks) }
