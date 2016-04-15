# gradle-codegen

This plugin makes it easy to integrate the generation of code into a 
gradle project without having to put the code into the buildSrc folder.
Dependencies are automatic, only add the plugin and specify the code
generation.

# Conventions
The plugin adds a number of tasks, configurations and sourcesets to the
build script. Note that names for the default sourceSet will, like other
names, not have default in their name. 

Type                                    | Name            | Function 
----------------------------------------|-----------------|----------
Configuration                           |generate         | The configuration used when running the generators
SourceSet                               |generators       | A SourceSet for building the generators. It's runtime classpath will be used for execution
Configuration                           |generatorsCompile| The configuration for compiling generators (implicitly, together with other configurations) through the SourceSet
NamedDomainObjectContainer<GenerateSpec>|generators       | Convention in SourceSets to specify file generators
Task                                    |generatorsCompile| The task responsible for compiling the generators.
Task                                    |generate         | The task to generate the code used in the SourceSet.
Output directory                        |outDir        

## How to use

There are two forms of code generation. One is for generators that 
create single files, the other is for files that work on a directory. 
While only one directory generator can be provided per task, there is 
no limit to the amount of file generators per sourceSet. Additional
generator tasks can be created, but they will not be linked up to the
source sets automatically.

Configurators are loaded by a separate classloader. In principle all 
generators in a task will use the same classloader, except in case that
a generator specifies its own classpath, in which case it will get its
own classloader.

### File generators
A file generator is a class that has one of the following possible 
signatures (the first parameter will be passed a `Writer`, must be 
able to accept a writer and be Appendable or a subtype), the second 
will be provided with the value of the `input` parameter in the 
configuration):
  
* `doGenerate(Appendable, T)`
* `doGenerate(Writer, T)`
* `doGenerate(Appendable)` -- only valid if the value of the `input` 
  configuration is `null`
* `doGenerate(Writer)` -- only valid if the value of the `input` 
  configuration is `null`

The return type of all functions is ignored. Type `T` must be a type 
that accepts the value of the `input` configuration variable. A 
generator method can be static or an instance method. In case of an 
instance method, the containing class must have a zero-argument
constructor. 

This is
used from gradle as follows:
```Gradle
sourceSets {
    main {
        generate {
            myNameInTheBuildScript {
                output = 'java/org/example/outputpackage/GeneratedClass.java'
                generator = "org.example.generators.MyGenerator"
                input = "Some arbitrary value of any type"
            }
        }
    }
}
```

Each `GenerateSpec` block has the following parameters:

Name     | Type         | Description
---------|--------------|--------------
input    |`Object`      | Any value at all, or none. This is provided as a parameter to the generator.
generator|`String`      | The name of the class to load.
classpath|FileCollection| A configurator specific set of jars and directories to use for loading classes. The classpath of the configuration will always be included as well.
output   |`String`      | The name of the file to write the output to.

### Directory generators
Directory generators are specified on the GenerateTask itself. Directory 
generators are structured like file generators but do not have writers
passed to them. Directory generators are classes with a `doGenerate` 
method as follows:

* `doGenerate(File, T)`
* `doGenerate(Appendable)` -- only valid if the value of the `input` 

Type `T` is the type provided as input in the configuration of the
GeneratorTask. Directory generators are used as follows:
```Gradle
generate { // predefined task
    outputDir = 'gen/main' // Defaults to gen/${sourceSetName} when autoCreated or gen for standalone tasks
    classpath = files( 'pathToJar' ) // Defaults to the classpath of the configuation with the same name as the task 
    dirGenerator {
        outputDir = 'java/org/example/outputpackage/GeneratedClass.java'
        classpath = null // Specific additional items to put on the classpath 
                        // for this generator only. The used classpath combines 
                        // this and the task specific one 
        generator = "org.example.generators.MyGenerator"
        input = "Some arbitrary value of any type"
    }
}
```