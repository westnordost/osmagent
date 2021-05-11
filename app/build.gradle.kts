import java.util.Properties
import java.net.URI
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-android-extensions")
    kotlin("plugin.serialization") version "1.4.30"
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    signingConfigs {
        create("release") {

        }
    }

    compileSdkVersion(30)
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    defaultConfig {
        applicationId = "de.westnordost.streetcomplete"
        minSdkVersion(17)
        targetSdkVersion(30)
        versionCode = 3201
        versionName = "32.0-alpha2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // don't use proguard-android-optimize.txt, it is too aggressive, it is more trouble than it is worth
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            applicationIdSuffix = ".debug"
        }
    }

    lintOptions {
        disable("MissingTranslation")
        isAbortOnError = false
    }
}



val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    val props = Properties()
    props.load(FileInputStream(keystorePropertiesFile))
    val releaseSigningConfig = android.signingConfigs.getByName("release")
    releaseSigningConfig.storeFile = file(props.getProperty("storeFile"))
    releaseSigningConfig.storePassword = props.getProperty("storePassword")
    releaseSigningConfig.keyAlias = props.getProperty("keyAlias")
    releaseSigningConfig.keyPassword = props.getProperty("keyPassword")
}

repositories {
    mavenLocal()
    jcenter()
    maven { url = URI("https://jitpack.io") }
}

configurations {
    // it's already included in Android
    all {
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
}

dependencies {
    val kotlinVersion = "1.4.10"
    val mockitoVersion = "3.7.7"
    val kotlinxVersion = "1.4.2"
    val daggerVersion = "2.31.2"

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")

    // tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-inline:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:2.8.0")

    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test:rules:1.3.0")
    androidTestImplementation("org.mockito:mockito-android:$mockitoVersion")
    androidTestImplementation("org.assertj:assertj-core:2.8.0")

    // dependency injection
    implementation("com.google.dagger:dagger:$daggerVersion")
    kapt("com.google.dagger:dagger-compiler:$daggerVersion")

    // Android stuff
    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.annotation:annotation:1.2.0")
    implementation("androidx.fragment:fragment-ktx:1.3.3")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.0")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.0.0")

    // photos
    implementation("androidx.exifinterface:exifinterface:1.3.2")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinxVersion")

    // scheduling background jobs
    implementation("androidx.work:work-runtime:2.5.0")

    // finding in which country we are for country-specific logic
    implementation("de.westnordost:countryboundaries:1.5")
    // finding a name for a feature without a name tag
    implementation("de.westnordost:osmfeatures-android:2.1")
    // talking with the OSM API
    implementation("de.westnordost:osmapi-map:2.0")
    implementation("de.westnordost:osmapi-changesets:2.0")
    implementation("de.westnordost:osmapi-notes:2.0")
    implementation("de.westnordost:osmapi-user:2.0")
    implementation("com.squareup.okhttp3:okhttp:3.12.12")
    implementation("se.akerfeldt:okhttp-signpost:1.1.0")

    // widgets
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("me.grantland:autofittextview:0.2.1")
    implementation("org.sufficientlysecure:html-textview:3.9")
    implementation("com.duolingo.open:rtl-viewpager:2.0.0")
    implementation("com.google.android:flexbox:2.0.1")

    // box2d view
    implementation("org.jbox2d:jbox2d-library:2.2.1.1")

    // serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")

    // map and location
    implementation("com.mapzen.tangram:tangram:0.16.1")

    // config files
    implementation("com.esotericsoftware.yamlbeans:yamlbeans:1.15")

    // opening hours parser
    implementation("ch.poole:OpeningHoursParser:0.23.0")

    // sunset-sunrise parser for lit quests
    implementation("com.luckycatlabs:SunriseSunsetCalculator:1.2")
}

/** Localizations that should be pulled from POEditor etc. */
val bcp47ExportLanguages = setOf(
    "ar","ast","bg","ca","cs","da","de","el","en","en-AU","en-GB","es","eu",
    "fa","fi","fr","gl","hr","hu","id","it", "ja","ko","lt","ml","nb","no","nl","nn",
    "pl","pt","pt-BR","ro","ru","sk","sr-cyrl","sv","tr","uk","zh","zh-CN","zh-HK","zh-TW"
)

tasks.register<UpdatePresetsTask>("updatePresets") {
    group = "streetcomplete"
    languageCodes = bcp47ExportLanguages
    targetDir = "$projectDir/src/main/assets/osmfeatures/default"
}

tasks.register<UpdateNsiPresetsTask>("updateNsiPresets") {
    group = "streetcomplete"
    targetDir = "$projectDir/src/main/assets/osmfeatures/brands"
}

tasks.register<UpdateAppTranslationsTask>("updateTranslations") {
    group = "streetcomplete"
    languageCodes = bcp47ExportLanguages
    apiToken = properties["POEditorAPIToken"] as String
    targetFiles = { "$projectDir/src/main/res/values-$it/strings.xml" }
}

tasks.register<UpdateAppTranslationCompletenessTask>("updateTranslationCompleteness") {
    group = "streetcomplete"
    apiToken = properties["POEditorAPIToken"] as String
    targetFiles = { "$projectDir/src/main/res/values-$it/translation_info.xml" }
}

tasks.register<GenerateMetadataByCountry>("generateMetadataByCountry") {
    group = "streetcomplete"
    sourceDir = "$rootDir/res/country_metadata"
    targetDir = "$projectDir/src/main/assets/country_metadata"
}

tasks.register("copyDefaultStringsToEnStrings") {
    doLast {
        File("$projectDir/src/main/res/values/strings.xml")
            .copyTo(File("$projectDir/src/main/res/values-en/strings.xml"), true)
    }
}
