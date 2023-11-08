package com.example.test_plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        System.out.println("第三种方式实现插件my plugin");
    }
}