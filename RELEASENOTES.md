### 0.5.8:
* Much better diagnostics and error handling
* Instead of requiring the input to be realized immediately, it can be a
  Callable instead, that will be resolved if there is no doGenerate method
  that takes Callables. The result of the callable is used to resolve the
  actually invoked doGenerate method.
* Allow for absolute paths, by making sure not to resolve them against
  the base directory when they are absolute.

### 0.5.7: 
* Resolve most things lazilly. In particularly only set the "files" for the generate
  configuration afterwards. Fixes dependency configuration.

* Be more free in accepting null values on any type of second parameter, as long
  as it is not annotated as being NotNull.

### 0.5.6: 
* Make the dependency work properly, by add a builtBy to the files needed by the compile Configuration. No more
  guessing of alternative language task names.

### 0.5.5: 
* Add an extension to the generate task that allows directory wide generation
  of unspecified. files. This is mainly useful for annotation processing.

### 0.5.1: 
* Don't have a hard dependency on kotlin.

### 0.5: 
* Initial release