package Database;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemSaver {


    private RandomAccessFile fileSave;
    private UserDefinedFileAttributeView attributeView;
    private String itemName;
    // the path where the database item records are written to
    public static final String outputPath = System.getProperty("user.dir")+"/Database/records/";
    // the "key" where the indexes for the items are stored
    private static final String indexAttribute = "indexes";
    private List<Long> indexes;


    public ItemSaver(String itemName) {
        // replace all the . with / so the tree heirarchy is maintained with packages
        this.itemName = itemName.replace(".", "/");
        try {
            // make the file and ensure it exists
            Utils.openFile(outputPath + this.itemName + ".rec");
            // open the file as a random access file
            fileSave = new RandomAccessFile(outputPath + this.itemName + ".rec", "rw");
            // move to the end to append to the file
            fileSave.seek(fileSave.length());
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            System.exit(1);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        // open the attributes, a.k.a metadata of the file
        attributeView = Files.getFileAttributeView(Paths.get(outputPath+ this.itemName + ".rec"), UserDefinedFileAttributeView.class);
        try {
            // if we have previously stored the indexes read them into an arraylist
            if (attributeView.list().contains(indexAttribute))
                indexes = Arrays.stream(Utils.getMetadata(attributeView, indexAttribute).split(",")).map(Long::valueOf).collect(Collectors.toList());
                // otherwise make a new arraylist and add index 0 for the beginning
            else {
                indexes = new ArrayList<>();
                indexes.add((long)0);
            }

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    // save multiple items
    public void saveItem(Serializable... objects) {
        // for every object save the object
        for (Serializable object : objects)
            saveItem(object);
    }

    // save an object at the end of a file
    public void saveItem(Serializable serializableObject){
        try {
            saveItem(serializableObject, fileSave.length());
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    // save an object at a given index
    private void saveItem(Serializable serializableObject, long index){
        // check if the object we are trying to save is of the type this saver saves
        if (!serializableObject.getClass().getSimpleName().equalsIgnoreCase(itemName.split("/")[itemName.split("/").length-1])) {
            System.out.printf("%s does not equal %s\n", serializableObject.getClass().getSimpleName(), itemName.split("/")[itemName.split("/").length-1]);
            return;
        }
        try {
            // move to the index
            fileSave.seek(index);
            // convert the objects to an array of bytes
            byte[] bytes = objectToBytes(serializableObject);
            if (bytes==null){System.out.println("Bytes are null");return;}
            // write the bytes to the file
            fileSave.write(bytes);
            // add the index to the index tables
            appendIndex(fileSave.length());
        }catch (IOException e){
            e.printStackTrace();
        }


    }

    // read an object from a given index
    public Object readItem(int index){
        // check if the index is out of range
        if (index+1>=indexes.size()) return null;
        try {
            // go to the index
            fileSave.seek(indexes.get(index));
            // get the size of the object we are reading in
            long size = indexes.get(index+1)-indexes.get(index);
            // make a byte array to store the object
            byte[] item = new byte[(int)size];
            // read the object into the array
            fileSave.readFully(item);
            // return the bytes converted into an object
            return bytesToObject(item);
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    // reads all items into an array and returns the array
    public Object[] readAllItems(){
        // make an array of objects
        Object[] objects = new Object[indexes.size()-1];
        // for each position get the object
        for (int counter = 0; counter<objects.length; counter++)
            objects[counter] = readItem(counter);
        // return the array of objects
        return objects;
    }

    // get the number of objects
    public int numObjects() {
        return indexes.size()-1;
    }

    // converts a serializable object to bytes
    private byte[] objectToBytes(Serializable object){
        byte[] toReturn = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            // make an object output stream
            out = new ObjectOutputStream(bos);
            // write the object to the output stream
            out.writeObject(object);
            out.flush();
            // get the stream as an array of bytes to return
            toReturn = bos.toByteArray();
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
        // make sure we close all output streams
        finally {
            Utils.close(bos);
        }

        // return the array
        return toReturn;

    }

    // convert bytes to an object
    private Object bytesToObject(byte[] bytes){
        // make our streams
        Object toReturn = null;
        ByteArrayInputStream bos = new ByteArrayInputStream(bytes);
        ObjectInput in = null;
        try {
            in = new ObjectInputStream(bos);
            // read our bytes to an object
            toReturn = in.readObject();
        }catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
            return null;
        } finally {
            Utils.close(bos);
        }
        // return our object
        return toReturn;
    }

    // get the indexes of an array of objects in the file
    public Integer[] getIndexes(Serializable... objects) {
        ArrayList<Integer> indexes = new ArrayList<>();
        int counter = 0;
        // for every object
        for (Object object : readAllItems()) {
            // check if the object matches an object in the array, if so add the index
            for (Object check : objects)
                if (object.equals(check))
                    indexes.add(counter);
            counter++;
        }
        // return the index arraylist as an array
        return indexes.toArray(new Integer[0]);
    }

    // delete a single or multiple items
    public void deleteItem(Integer... index) {
        try {
            // make a temp file
            RandomAccessFile newFile = new RandomAccessFile(outputPath + itemName + ".tmp", "rw");

            // put the object's byte indexes to skip into an arraylist
            ArrayList<Long> skip = new ArrayList<>();
            for (long skipIndex : index)
                skip.add(indexes.get((int)skipIndex));

            // move to the beginning of the file
            int currentIndex = 0;
            fileSave.seek(0);
            // for every record in the file
            while (fileSave.getFilePointer() < fileSave.length()) {
                // if the index we are at is to be skipped then move over the record to the next record
                if (skip.contains(fileSave.getFilePointer())){ currentIndex++; fileSave.seek(indexes.get(currentIndex));continue;}
                // calculate the size and write the object to the temp file
                long size = indexes.get(currentIndex + 1) - indexes.get(currentIndex);
                byte[] item = new byte[(int) size];
                fileSave.readFully(item);
                newFile.write(item);
                currentIndex++;
            }


            // here we will be shifting the indexes to account for the removed files
            long shift = 0;
            List<Long> newIndexes = new ArrayList<>();
            for (int counter = 0; counter<indexes.size(); counter++){
                // increase the shift by the size of the record we are skipping
                if (skip.contains(indexes.get(counter))) shift+=indexes.get(counter+1)-indexes.get(counter);
                    // shift the remaining indexes in the file
                else
                    newIndexes.add(indexes.get(counter)-shift);

            }

            // close both files
            newFile.close();
            fileSave.close();
            // reset the indexes and add the shifted indexes
            indexes.removeAll(indexes);
            indexes.addAll(newIndexes);

            // rename the temp file to the actual file overriding the old file
            Utils.openFile(outputPath + itemName + ".tmp").renameTo(Utils.openFile(outputPath + itemName + ".rec"));
            // reopen the new file as the file to be saving the objects to and move to the end to append
            fileSave = new RandomAccessFile(outputPath + itemName + ".rec", "rw");
            fileSave.seek(fileSave.length());
            // open the metadata of the new file and write the new indexes
            attributeView = Files.getFileAttributeView(Paths.get(outputPath+ itemName + ".rec"), UserDefinedFileAttributeView.class);
            attributeView.write(indexAttribute, Charset.defaultCharset().encode(String.join(",", indexes.stream().map(Object::toString).toArray(String[]::new))));
        }catch (IOException e){
            e.printStackTrace();
        }


    }

    // adds an index to the metadata of the file to maintain the index table
    private void appendIndex(long index){
        // add the index to the arraylist
        indexes.add(index);
        try {
            // write the new indexes to the object's metadata
            attributeView.write(indexAttribute, Charset.defaultCharset().encode(String.join(",", indexes.stream().map(Object::toString).toArray(String[]::new))));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
