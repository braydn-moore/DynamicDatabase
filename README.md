## Dynamic Database
A database written in Java which allows for the creation of object files by dynamically creating a .java file and compiling it. All classes are written and accessed using serialization and reflection to handle the dynamic nature of the objects and modifies the JVM at runtime to access the loaded classes. Users can create a login and the password is hashed using the Blowfish password hashing scheme.

## Getting Started
This application require Java 8 to run as Oracle removed some of the less than safe methods we used to modify the JVM. A prebuilt jar can be run using

    java -jar run.jar

Or you can compile the following code with javac making sure to include the flag

    -source 1.8

to tell the compiler the code requires Java 8 to be built.
