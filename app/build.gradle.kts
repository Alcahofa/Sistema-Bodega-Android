plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.secrets)
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

android {
    namespace = "com.example.sqlite"
    testBuildType = "phase2Test"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sqlite"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("phase2Test") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".phase2test"
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.code.scanner)
    implementation(libs.play.services.maps)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
