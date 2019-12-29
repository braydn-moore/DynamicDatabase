package Database;

import java.io.*;
import java.util.Scanner;

public class Login {

    private static BufferedReader reader;
    private static boolean isConsole;

    private static String readPassword(String prompt, Scanner scanner) {
        // check if we are in a console environment to try to mask the password
        if (isConsole)
            return new String(System.console().readPassword(prompt));
        // if we are not accept the password as regular input
        System.out.print(prompt);
        return scanner.nextLine();
    }

    // try to log the user in
    public static boolean login(Scanner scanner) {
        // set our variable for the environment we are in
        isConsole = System.console() != null;
        // if the config file doesn't exist then make a new user
        if (!Utils.exists(System.getProperty("user.dir") + "/Database/config")) {

            // make our writer
            BufferedWriter writer = null;
            try {
                // make the config file
                writer = new BufferedWriter(new FileWriter(Utils.openFile(System.getProperty("user.dir") + "/Database/config")));
                // get and write the user name
                System.out.println("Welcome new user!");
                System.out.print("Please enter your name: ");
                writer.write(scanner.nextLine() + ":");
                // get the user password and write it to the file using an irreversible hash(derived from the blowfish algorithm)
                // with a custom salt
                writer.write(BCrypt.hashpw(readPassword("Please enter your password: ", scanner), BCrypt.gensalt()));
                // allow the user to login
                return true;
            } catch (IOException e) {
                // if something went wrong tell the user and exit the program
                System.out.println("Sorry. I couldn't make a new account");
                return false;
            }
            // close the writer
            finally {
                Utils.close(writer);
            }
        }

        // if there is a pre-existing user
        else {
            // get the users name and hashed password
            String user;
            try {
                reader = new BufferedReader(new FileReader(Utils.openFile(System.getProperty("user.dir") + "/Database/config")));
                user = reader.readLine();
            } catch (IOException e) {
                System.out.println("Couldn't open password file");
                return false;
            } finally {
                Utils.close(reader);
            }
            String hash = user.split(":")[1];
            System.out.printf("Welcome %s\n", user.split(":")[0]);
            // get the user's password and compare the hashes to what they entered, if it is even then let them in
            if (BCrypt.checkpw(readPassword("Please enter your password: ", scanner), hash))
                return true;
                // otherwise give them 5 more tries to login
            else
                for (int counter = 5; counter > 0; counter--)
                    if (BCrypt.checkpw(readPassword(String.format("Incorrect. Please try again. %d tries remaining: ", counter), scanner), hash))
                        return true;

            // if we get to this point the user has exhausted their chances so kick them out
            return false;
        }
    }
}
