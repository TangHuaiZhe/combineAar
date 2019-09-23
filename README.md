# combineAar

[![996.icu](https://img.shields.io/badge/link-996.icu-red.svg)](https://996.icu)

实现合并Android Aar项目/文件，支持多个Flavor对应


```gradle
dependencies {
    embed project(path: ':module1', configuration: 'default')
    embed project(path: ':module2', configuration: 'default')
    }
```

集成:
```gradle
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.com.tangnb.plugin:CombineAar:1.0.4-SNAPSHOT"
  }
}

//在你的aar壳工程中:
apply plugin: "com.tangnb.CombineAar"
```

然后运行aar你壳工程moduleMain的编译任务:
`./gradlew moduleMain:assembleFlavor1Flavor2BuildType`

如你的壳工程是main,Flavor有两个维度:pay和api26,编译release版本: 
`./gradlew main:assemblePayApi26Release`
