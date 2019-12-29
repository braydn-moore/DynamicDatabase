package Database;

import sun.misc.Unsafe;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import static java.util.Collections.singletonList;
import static javax.tools.JavaFileObject.Kind.SOURCE;


public class ItemMaker{
    // where we output the compiled classes
    public static final String classOutputPath = System.getProperty("user.dir")+"/Database/";
    // template for all methods
    private static final String methodTemplate = "$ACCESS$ $RETURN$ $NAME$($PARAMETERS$){\n" +
            "   $CODE$;\n" +
            "}\n\n";
    private String className;
    private String filePath;
    // logistics for the path output
    private String path;
    // the template for every class
    // the names surrounded by $$ are to be replaced by dynamic pieces as the user chooses
    private String classBuilder = "" +
            "package $PACKAGE$;" +
            "import java.io.Serializable;\n" +
            "import java.lang.reflect.Field;\n" +
            "\n" +
            "public class $CLASSNAME$ implements Serializable{\n" +
            "   $FIELDS$\n" +
            "\n" +
            "   public $CLASSNAME$($CONSTRUCTORPARAMETERS$){\n" +
            "       $CONSTRUCTOR$\n" +
            "   }\n" +
            "   @Override\n" +
            "    public boolean equals(Object compare){\n" +
            "        if (!this.getClass().getName().equals(compare.getClass().getName())) return false;\n" +
            "\n" +
            "        try {\n" +
            "            for (Field field : this.getClass().getDeclaredFields())\n" +
            "                if (!field.get(compare).equals(field.get(this))) return false;\n" +
            "            return true;\n" +
            "        }catch (IllegalAccessException e){\n" +
            "            return false;\n" +
            "        }\n" +
            "    }" +
            "\n" +
            "   $METHODS$\n" +
            "\n" +
            "}";
    // if we need to build the class or if it already exists
    private boolean needToBuild = true;


    // string buidlers for fields, methods and the constructor
    private StringBuilder fields;
    private StringBuilder methods;
    private StringBuilder constructor;
    // arraylist of variables
    private ArrayList<Variable> variables = new ArrayList<>();


    // Constructor
    public ItemMaker(String fullPath, String output) {
        // get the path for the package minus the class name
        path = String.join(".", removeLastItem(fullPath.split("\\.")));
        // sanitize the class name
        className = fullPath.replace(path.replace("Database.", "") + ".", "");
        // get the path to store the file
        filePath = path.replace('.', '/') + "/" + className;
        // if the class already exists we don't have to build the class again unless the user chooses to update it
        if (Utils.exists(classOutputPath + filePath + ".class")) needToBuild = false;

        // initialize the string builders
        fields = new StringBuilder();
        constructor = new StringBuilder();
        methods = new StringBuilder();
        // add a simple to string function as a debugging function to be able to quickly print the type
        methods.append("public String toString(){\n").append("return \"$OUTPUT$\";\n".replace("$OUTPUT$", output)).append("}");
    }

    // remove the last item of a string array
    private String[] removeLastItem(String[] arr){
        return Arrays.copyOf(arr, arr.length-1);
    }

    // creates a method by appending to the string builder a method template with the dynamic pieces replaced by the given parameters
    public void addMethod(String access, String returnType, String name, String code, String... parameters){
        methods.append(methodTemplate.replace("$ACCESS$", access).replace("$RETURN$", returnType).replace("$NAME$", name)
                .replace("$PARAMETERS$", String.join(", ", parameters)).replace("$CODE$", code));
    }

    // adds a variable to the class
    public void addVar(String varType, String varName){
        // make sure there are no spaces in the variable name
        varName = varName.replace(" ", "");
        // add the new variable
        variables.add(new Variable(varType, varName));
        // add the variable to the fields
        fields.append(varType).append(" ").append(varName).append(";\n");
        // add getters and setters to the class
        addMethod("public", varType, "get"+Utils.capitalize(varName), "return "+varName);
        addMethod("public", "void", "set"+Utils.capitalize(varName), String.format("this.%s=%s", varName, varName), String.format("%s %s", varType, varName));
        // add a setter in the constructor
        constructor.append("this.").append(varName).append("=").append(varName).append(";");
    }

    // make the class getting update as a boolean
    public void makeClass(boolean update){
        // if the user doesn't want to update and the class is already built ignore it
        if (!needToBuild && !update) return;
        // replace the dynamic parts of the class templates such as the package, class name, constructor, fields, methods,
        // constructor parameters, etc.
        classBuilder = classBuilder.replace("$PACKAGE$", path).replace("$CLASSNAME$", className)
                .replace("$FIELDS$", fields.toString()).replace("$CONSTRUCTOR$", constructor.toString())
                .replace("$METHODS$", methods.toString())
                // conver the arraylist of variables to parameters for the constructor
                .replace("$CONSTRUCTORPARAMETERS$", String.join(", ",variables.stream().map(Variable::toString).toArray(String[]::new)));


        // make the byte output stream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // create the java file with teh class builder and our output stream
        SimpleJavaFileObject file = new SimpleJavaFileObject(URI.create(filePath+".java"), SOURCE){
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return classBuilder;
            }

            @Override
            public OutputStream openOutputStream() {
                return outputStream;
            }
        };

        // make a new file manager for the java object created above
        JavaFileManager fileManager = new ForwardingJavaFileManager(
                ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {

            @Override
            public JavaFileObject getJavaFileForOutput(Location location,
                                                       String className,
                                                       JavaFileObject.Kind kind,
                                                       FileObject sibling) {
                return file;
            }
        };

        // get the system compiler and add a task to compile our brand new java object file
        ToolProvider.getSystemJavaCompiler().getTask(null, fileManager, null, null, null, singletonList(file)).call();
        // save the binary compiled version of our class
        saveClassFile(outputStream);
        try {
            // define the new class for the interpreter so we can use it without having to restart the program
            define(outputStream.toByteArray());
        }catch (Exception e){e.printStackTrace();}
    }

    // define a new class for the interpreter
    private void define(byte[] classData) throws NoSuchFieldException, IllegalAccessException{
        // in case you couldn't tell Java reaaaallllllyyyy wants you to know it's unsafe to do this
        // but it's ok because I'm a pro
        // get the field called theUnsafe
        // oooooo ominous
        final Field f = Unsafe.class.getDeclaredField("theUnsafe");
        // set it to public because it's naturally private
        // yea. I know access modifiers have no power with me
        // I'm basically a god
        f.setAccessible(true);
        // get the unsafe object
        final Unsafe unsafe = (Unsafe) f.get(null);
        // define the class
        final Class aClass = unsafe.defineClass(path+"."+className, classData, 0, classData.length, ClassLoader.getSystemClassLoader(), null);
    }


    // save the class file
    private void saveClassFile(ByteArrayOutputStream classData){
        // create the empty class file
        Utils.openFile(classOutputPath+filePath+".class");
        OutputStream stream = null;
        try{
            // open the file
            stream = new FileOutputStream(classOutputPath+filePath+".class");
            // write the new class
            classData.writeTo(stream);
            stream.flush();
        }catch (IOException e){
            System.out.println("Error writing class for "+filePath);
            e.printStackTrace();
        } finally {
            // close the stream
            Utils.close(stream);
        }
    }


    // to string for the item maker
    public String toString(){
        return String.format("Path:%s\nClass Name:%s", path, className);
    }


    // basically a holding class for the variables of the clas
    private static class Variable{

        // type and name of the variable
        String type, name;

        // set the type and name
        Variable(String type, String name){
            this.name = name;
            this.type = type;
        }

        // to string to format for the creation of the class
        @Override
        public String toString(){
            return type+" "+name;
        }

    }



}
