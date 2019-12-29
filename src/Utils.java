package Database;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.attribute.UserDefinedFileAttributeView;

public class Utils {

    // get the metadata of a file given the attribute accessor and the name of the attribute
    public static String getMetadata(UserDefinedFileAttributeView view, String name) throws IOException{
        // make a buffer of the size of the attribute
        ByteBuffer buf = ByteBuffer.allocate(view.size(name));
        // read in the data
        view.read(name, buf);
        // flip the data
        buf.flip();
        // return the bytes as a String
        return Charset.defaultCharset().decode(buf).toString();
    }

    public static boolean isNumber(String input) {
        for (char test : input.toCharArray())
            if (test < '0' || test > '9')
                return false;
        return true;
    }

    public static boolean isNumberVarargs(String input, String delimeter) {
        for (String string : input.split(delimeter))
            if (!isNumber(string))
                return false;
        return true;
    }

    // open/create a file and all the dirs if it doesn't exist
    public static File openFile(String path){
        // get the path as an array
        String[] points = path.split("/");
        // get dirPath
        String directoryPath = path.replace(points[points.length-1], "");
        points = null;
        // make the directories needed
        File dirPath = new File(directoryPath);
        if (!dirPath.exists()) dirPath.mkdirs();
        dirPath = null;
        // make a new file and return it
        return new File(path);
    }

    // return if a file exists or not
    public static boolean exists(String path){
        return new File(path).exists();
    }

    // close a closeable object in the finally portion of a try-catch in one easy step
    public static void close(Closeable toClose){
        if (toClose == null) return;
        try{
            toClose.close();
        }catch (IOException e){
            System.out.printf("Couldn't close "+toClose.toString());
        }
    }

    // get the last element of an array
    public static<T> T lastElement(T[] arr){
        return arr[arr.length-1];
    }

    // capitalize a string
    public static String capitalize(String input){
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
