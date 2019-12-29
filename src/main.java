package Database;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class main {

    private static HashMap<String, Boolean> doesExist = new HashMap<>();
    private static HashMap<String, ItemSaver> savers;
    private static Scanner scanner;

    // add a path to the java "importer" for our .class files we dynamically compile
    private static void addPath(String s) throws NoSuchMethodException, MalformedURLException, IllegalAccessException, InvocationTargetException {
        URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        // get the add url method and set it to be accesible through reflection
        Class urlClass = URLClassLoader.class;
        Method method = urlClass.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        // add the new url a.k.a file path to the class loader
        method.invoke(urlClassLoader, new File(s).toURI().toURL());
    }

    // ONLY WORKS IN THE CONSOLE
//    private static void restart(){
//        try {
//            final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
//            final File currentJar = new File(main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
//
//            /* is it a jar file? */
//            if (!currentJar.getName().endsWith(".jar"))
//                return;
//
//            /* Build command: java -jar application.jar */
//            final ArrayList<String> command = new ArrayList<String>();
//            command.add(javaBin);
//            command.add("-jar");
//            command.add(currentJar.getPath());
//
//            final ProcessBuilder builder = new ProcessBuilder(command);
//            builder.start();
//        }catch (URISyntaxException | IOException e){
//            System.out.println("Unable to restart, just exiting");
//        }
//        finally {
//            System.exit(0);
//        }
//    }

    private static void changePassword(Scanner scanner) {
        // get the users new password
        String newPassword;
        if (System.console() != null)
            newPassword = new String(System.console().readPassword("Enter your new password: "));
        else {
            System.out.print("Enter your new password: ");
            newPassword = scanner.nextLine();
        }

        // if the config got deleted exit and when the user reruns the program the file will be made
        if (!Utils.exists("Database/config")) {
            System.out.println("Config file missing");
            System.exit(1);
        }

        // make our readers and writers
        BufferedReader passwordReader = null;
        BufferedWriter passwordWriter = null;
        try {
            // get the current config
            passwordReader = new BufferedReader(new FileReader("Database/config"));
            String currentConfig = passwordReader.readLine();
            Utils.close(passwordReader);
            // write the new config to the config file
            passwordWriter = new BufferedWriter(new FileWriter("Database/config"));
            String name = currentConfig.split(":")[0];
            passwordWriter.write(name);
            passwordWriter.write(":");
            passwordWriter.write(BCrypt.hashpw(newPassword, BCrypt.gensalt()));

        } catch (IOException e) {
            System.out.println("Unable to open the file");
            System.exit(1);
        }
        // close both of the streams at the end regardless of error etc.
        finally {
            Utils.close(passwordReader);
            Utils.close(passwordWriter);
        }
    }

    // check if a class has been loaded
    private static boolean isClassLoaded(String classLoad) {
        // if the class is in the hashmap then return its value
        if (doesExist.containsKey(classLoad)) return doesExist.get(classLoad);
        try {
            // check if the class can be made
            Class.forName(classLoad);
            // if we can set that it is a valid class
            doesExist.put(classLoad, true);
            return true;
        } catch (ClassNotFoundException e) {
            // if the class is not found set the value as false
            doesExist.put(classLoad, false);
            return false;
        }
    }



    // get user input using a prompt style interface
    private static String getInput(String prompt) {
        System.out.print(prompt + ">>>");
        return scanner.nextLine();
    }

    // dynamically create a new class using user input
    private static void makeClass(String className, boolean update){
        // make a new item maker
        ItemMaker maker = new ItemMaker("items." + className, "This is a class of type " + className);

        String varName;
        String varType;

        // while the user wants to input variables
        while (true) {
            // get the name of the variable
            varName = getInput("What is the var name");
            // leave if there is no input
            if (varName.equalsIgnoreCase(""))
                break;

            // if the variable name starts with a digit then error
            else if (Character.isDigit(varName.charAt(0))){
                System.out.println("\tVariables cannot start with a number");
                continue;
            }

            // get the data type for the field
            while (true) {
                varType = getInput("What is the type of " + varName);
                // if the variable is a valid type then exit the loop
                if (varType.equals("String") || varType.equals("Integer") || varType.equals("Boolean") || varType.equals("Double") || varType.equals("Long"))
                    break;
                System.out.printf("\tThat's not a supported type\n");
            }

            // add the variable to the class
            maker.addVar(varType, varName);
        }

        // make the class and update it if the user requests such
        maker.makeClass(update);
        // add the class to the does exist hashmap
        doesExist.put("items." + className, true);
        // add an item saver for the new class
        savers.put("items." + className, new ItemSaver("items." + className));
    }

    // override the makeClass to get the class name from user input and then call
    private static void makeClass(boolean update) {
        String className = getInput("What class do you want to make");
        makeClass(className, update);
    }


    // get a class from the string
    private static Class getClass(String className) {
        // if the class is not loaded return null
        if (!isClassLoaded(className)) return null;
        try {
            // return the class
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            // this should never get here because of previous checks but just in case
            System.out.println("Class not found");
            return null;
        }
    }

    // re-initialize the savers based on the files that are on the disk
    private static void initSavers() {
        savers = new HashMap<>();
        try {
            if (!Utils.exists("Database/records/"))
                return;
            // recursively get all files in the path
            for (Path path : Files.walk(Paths.get("Database/records/"))
                    .filter(Files::isRegularFile).collect(Collectors.toList())) {
                // get the class name of the file
                String className = path.toString().replace(".rec", "").replace("Database/records/", "").replace("/", ".");
                // initialize a saver for said class/file
                savers.put(className, new ItemSaver(className));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to init savers");
        }
    }

    // dynamicaly create a new object
    private static Serializable makeObject(String className) {
        className = "items."+className;
        // make an array list of the arguments and the classes of the arguments for dynamic construction
        ArrayList<Class> constructorArgClasses = new ArrayList<>();
        ArrayList<Object> args = new ArrayList<>();
        // get the class
        Class classTest = getClass(className);
        // if there is no such class return nothing
        if (classTest == null)
            return null;
        Field field = null;
        try {
            // for every field in the class
            for (Field test : classTest.getDeclaredFields()) {
                // add an argument
                field = test;
                // add the class
                constructorArgClasses.add(field.getType());
                // dynamically cast the given value to the required type by relying on the fact that the user can only enter
                // Strings, Integers, Doubles, and Booleans which all have constructors where a string is a parameter to
                // cast to the given value
                args.add(field.getType().getDeclaredConstructor(String.class).newInstance(getInput(String.format("Enter the value of %s of type %s in %s", field.getName(), field.getType().getSimpleName(), className))));
            }

            // return a new instance of the class using the provided variables for the constructor ensuring it is a subclass of Serializble
            return Class.forName(className).asSubclass(Serializable.class).getDeclaredConstructor(constructorArgClasses.toArray(new Class[constructorArgClasses.size()])).newInstance(args.toArray());

        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            if (field != null)
                System.out.printf("Failed to cast value to %s\n", field.getType().getName());
        } catch (ClassNotFoundException e) {
            if (field != null)
                System.out.printf("Could not find class called %s with %s as data\n", Arrays.toString(constructorArgClasses.toArray()));
        }

        return null;
    }


    // add an object to the file record
    private static void addObject(Serializable object) {
        // get the item saver for the class or make a new item saver and save the object
        savers.getOrDefault(object.getClass().getName(), new ItemSaver(object.getClass().getName())).saveItem(object);
    }

    // save an object to the file given the type of the class
    private static void addObject(String className) {
        if (!isClassLoaded("items." + className))
            return;
        // get the saver or make a new saver and then save a new object the user must make
        savers.getOrDefault("items." + className, new ItemSaver("items." + className)).saveItem(makeObject(className));
    }

    // checks if a class has a field
    private static boolean hasField(String className, String check) {
        try {
            // get the field, if it is null then it doesn't exist
            return getClass("items." + className).getDeclaredField(check) != null;
        } catch (NoSuchFieldException e) {
            // if an exception is thrown it doesn't exist
            return false;
        }
    }

    // get the value of a field of an object reflectively
    private static Object getField(String className, String fieldName, Object object) {
        try {
            // get the class of the class name, get the method for the getter of the field and call that method on the given object
            return getClass("items." + className).getMethod("get" + Utils.capitalize(fieldName)).invoke(object);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // return null if we could not retrieve the object
            return null;
        }
    }

    // get an object from the records
    private static Serializable[] getObject(String className, String search) {
        // get the item from the file at the given index
        if (Utils.isNumber(search) && savers.containsKey("items." + className))
            return new Serializable[]{(Serializable) savers.get("items." + className).readItem(Integer.parseInt(search))};
            // otherwise search for all objects with the given item;
        else if (search.contains(":") && savers.containsKey("items." + className) && hasField(className, search.split(":")[0])) {
            ArrayList<Serializable> toReturn = new ArrayList<>();
            for (Object object : savers.get("items." + className).readAllItems())
                if (object instanceof Serializable && getField(className, search.split(":")[0], object) != null && getField(className, search.split(":")[0], object).toString().equals(search.split(":")[1]))
                    toReturn.add((Serializable) object);

            return toReturn.toArray(new Serializable[0]);
        }
        // if there is no item saver for the given class return nothing
        else return new Serializable[]{null};
    }

    // print out the field's values and type of an object
    private static void printFields(Object object, String spacer, String header){
        if (object == null) return;
        if (header == null) header = String.format("Fields of class %s:", object.getClass().getName());
        try {
            // print out the header
            System.out.println(header);
            // for every field output the type and the value using the getter
            for (Field field : object.getClass().getDeclaredFields()) {
                Method method = object.getClass().getDeclaredMethod("get" + Utils.capitalize(field.getName()));
                System.out.printf("%s%s:%s:%s\n", spacer, field.getName(), field.getType().getSimpleName(), method.invoke(object));
            }
        }catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    // intelligently cast an object to the needed value for the field of an object
    private static Object intelligentCast(Object object, String var, String value){
        try{
            // if the field is of type string just return the value
            if (object.getClass().getDeclaredField(var).getType().isAssignableFrom(String.class)) return value;
            // get the method to convert a string to the given type dynamically by relying on Java's valueOf functions in all
            // major classes
            Method convertMethod = object.getClass().getDeclaredField(var).getType().getMethod("valueOf", String.class);
            // convert the object
            return convertMethod.invoke(object, value);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        return null;
    }

    // check if the class has a given field
    private static boolean isField(Class type, String field){
        try{
            // get the field
            type.getDeclaredField(field);
            // if an error has not been thrown then it is a field
            return true;
        }catch (NoSuchFieldException e){
            // it's not a valid field so return false
            return false;
        }
    }

    private static void deleteObject(String className, String search) {
        if (Utils.isNumberVarargs(search, ","))
            deleteObject(className, (Integer[]) Arrays.stream(search.split(",")).mapToInt(Integer::parseInt).boxed().toArray(Integer[]::new));
        else {
            deleteObject(className, savers.get("items." + className).getIndexes(getObject(className, search)));
        }
    }

    // delete a list of object
    private static void deleteObject(String className, Integer... index) {
        // get the saver and delete the items
        savers.get("items."+className).deleteItem(index);
    }

    private static void editObject(String className, String search) throws InvocationTargetException {
        // get the given object
        Serializable[] objects = getObject(className, search);
        className = "items."+className;
        // exit if the object is invalid
        if (objects.length == 0 || objects[0] == null) {
            System.out.println("That's not a valid class");
            return;
        }

        Integer[] oldIndexes = savers.get(className).getIndexes(objects);

        // print the fields for the user
        for (int counter = 0; counter<objects.length; counter++)
            printFields(objects[counter], "\t", null);
        String fieldToSet = null;

        // until the user chooses to exit
        while (true) {
            // get the equation
            fieldToSet = getInput("Enter a statement(eg. variable=10)");

            // exit if requested
            if (fieldToSet.equals(""))
                break;

            try {
                // divide the equation into the var and the value
                String[] parts = fieldToSet.trim().replaceAll(" +", " ").split("=");
                parts[0] = parts[0].replace(" ", "");
                parts[1] = parts[1].trim();
                // if there is not exactly 2 parts it is not valid
                if (parts.length != 2) {
                    System.out.println("\tThat's not a valid statement");
                    continue;
                }
                // if it is not a valid field
                else if (!isField(objects[0].getClass(), parts[0])) {
                    System.out.println(parts[0] + " is not a field in " + objects[0].getClass().getName());
                    // print the fields of the class
                    System.out.printf("Fields are %s\n", String.join(", ", Arrays.stream(objects[0].getClass().getDeclaredFields()).map(Field::getName).toArray(String[]::new)));
                    continue;
                }

                // intelligently cast the new object
                Object newValue = intelligentCast(objects[0], parts[0], parts[1]);
                // get the setter using reflection and call it
                for (Serializable object : objects)
                    object.getClass().getMethod("set" + Utils.capitalize(parts[0]), getClass(object.getClass().getDeclaredField(parts[0]).getType().getName())).invoke(object, newValue);
            } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

        // get the saver
        ItemSaver saver = savers.get(className);
        // delete the old item
        saver.deleteItem(oldIndexes);
        // append the new item to the file
        saver.saveItem(objects);
        // tell the user where the new record is
        System.out.println("Item has been edited and resaved at the end of the file");
    }

    private static void printAll(String className){
        if (!savers.containsKey("items."+className)) return;
        Object[] objects = savers.get("items."+className).readAllItems();
        for (int counter = 0; counter<objects.length; counter++)
            printFields(objects[counter], "\t", String.format("Class %s at index %d", objects[counter].getClass().getName(), counter));
    }

    private static void help() {
        System.out.println("\nHelp:" +
                "\n\tmake object <update>" +
                "\n\tadd <object name>" +
                "\n\tdelete <object name> <index>" +
                "\n\tedit <object name> <index>" +
                "\n\tget <object name> <index>" +
                "\n\tget all <object name>" +
                "\n\tNOTE: To access items by value use <KEY>:<VALUE> instead of index");
    }



    public static void main(String[] args) throws Exception {
        // make the scanner
        scanner = new Scanner(System.in);
        if (!Login.login(scanner)) {
            System.out.println("Goodbye :)");
            System.exit(0);
        }
        // add the path where we output the compiled classes
        addPath(ItemMaker.classOutputPath);
        // initialize the savers
        initSavers();

        String input = null;
        while (true){
            input = getInput("");
            // exit if the user wishes
            if (input.equalsIgnoreCase("exit")) break;

            // complete the user task and sanitize the input by removing spaces etc.
            if (input.startsWith("make object"))
                makeClass(Boolean.valueOf(input.replace("make object", "").trim()));
            else if (input.startsWith("add"))
                addObject(input.replace("add", "").trim());
            else if (input.startsWith("delete")){
                String[] inputs = input.replace("delete", "").trim().split(" ");
                deleteObject(inputs[0], inputs[1]);
            }
            else if (input.startsWith("edit")){
                String[] inputs = input.replace("edit", "").trim().split(" ");
                editObject(inputs[0], inputs[1]);
            }

            else if (input.startsWith("get all"))
                printAll(input.replace("get all", "").trim());

            else if (input.startsWith("get")){
                String[] inputs = input.replace("get", "").trim().split(" ");
                for (Serializable object : getObject(inputs[0], inputs[1]))
                    printFields(object, "\t", null);
            } else if (input.startsWith("help"))
                help();

            else if (input.startsWith("passwd"))
                changePassword(scanner);
                // if it doesn't match anything it is an invalud command
            else
                System.out.println("Invalid command");

        }

    }
}
