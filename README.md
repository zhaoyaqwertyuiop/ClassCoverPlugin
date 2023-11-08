# Plugins
根目录下的build.gradle中添加依赖：

```groovy
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    } 
}

dependencies {
    classpath "com.github.zhaoyaqwertyuiop:Plugins:0.0.5"
}
```

在使用到的地方添加插件，目前可用的有ClassCoverPlugin：
```groovy
plugins {
    id 'com.android.application'
    id 'ClassCoverPlugin'
}
```
该插件可以让本地文件覆盖掉依赖中的文件，能很方便的修改第三方依赖的代码，例子参照app中修改com.github.mikephil.charting.utils.Utils.java 文件
这个文件是在implementation("com.github.PhilJay:MPAndroidChart:v3.0.3")包中的文件，使用ClassCoverPlugin插件后，在本地创建同包名同文件名的Utils.java文件，就可以完美替换掉依赖包中的文件
