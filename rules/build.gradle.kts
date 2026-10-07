plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
sourceSets.main { java.srcDir("../vendor/chesslib/src/main/java") }
dependencies { api("org.apache.commons:commons-lang3:3.18.0") }
