//Jackson Lee

import java.io.*;
import java.util.*;
public class DBTable {
    
    RandomAccessFile rows;//the file that stores the rows in the table
    long free;//head of the free list space for rows
    int numOtherFields;
    int[] otherFieldLengths;
    BTree t;
    
    private class Row {
        private int keyField;
        private char[][] otherFields;
        
        /*
         * Each row consists of unique key and one or more character array fields.
         * Each character array field is a fixed length field (for example 10 characters).
         * Each field can have a different length.
         * 
         * Fields are padded with null characters so a field with a length of
         * x characters always uses space for x characters
         */
        
        //Constructors and other Row methods
        
        private Row(int key, char[][] fields) {
            keyField = key;
            otherFields = fields;           
        }
        
        private Row(long addr) throws IOException {
            rows.seek(addr);
            
            keyField = rows.readInt();
            
            for(int i = 0; i < otherFields.length; i++) {
                for(int j = 0; i < otherFields[i].length; i++) {
                    otherFields[i][j] = rows.readChar();
                }
            }
        }
        
        private void writeRow(long addr) throws IOException {
            rows.seek(addr);
            
            rows.writeInt(keyField);
            
            for(int i = 0; i < otherFields.length; i++) {
                for(int j = 0; j < otherFields[i].length; j++) {
                    rows.writeChar(otherFields[i][j]);
                }
            }
        }
    }
    
    public DBTable(String filename, int[] fL, int bsize) throws IOException {
        /*
         * Use this constructor to create a new DBTable
         * fL is the lengths of the otherFields
         * fL.length indicates how many other fields are part of the row
         * bsize is the block size. It is used to calculate the order of the B+Tree
         * 
         * A B+Tree must be created for the key field in the table
         * 
         * If a file with name filename exists, the file should be deleted before
         * the new file is created.
         */
        
        otherFieldLengths = fL;
        numOtherFields = fL.length;
        free = 0;
        File path = new File(filename);
        
        if(path.exists()) {
            path.delete();
        }
        
        rows = new RandomAccessFile(path, "rw");
        
        rows.writeInt(numOtherFields);
        for(int i = 0; i < numOtherFields; i++) {
            rows.writeInt(fL[i]);
        }
        rows.writeLong(free);

        t = new BTree(filename+"Index", bsize);
    }
    
    public DBTable(String filename) throws IOException {
        //Use this constructor to open an existing table
        
        File path = new File(filename);
        
        rows = new RandomAccessFile(path, "rw");
        
        rows.seek(0);
        
        numOtherFields = rows.readInt();
        otherFieldLengths = new int[numOtherFields];
        
        for(int i = 0; i < numOtherFields; i++) {
            otherFieldLengths[i] = rows.readInt();
        }
        free = rows.readLong();
        
        t = new BTree(filename+"Index");
    }
    
    public boolean insert(int key, char[][] fields) throws IOException {
        //PRE: the length of each rows fields matches the expected length
        /*
         * If a row with the key is not in the table, the row is added and the method
         * returns true otherwise the row is not added and the method returns false.
         * 
         * The method must use the B+Tree to determine if a row with the key exists.
         * 
         * If the row is added the key is also added into the B+Tree.
         */
        
        Row r = new Row(key, fields);
        long addr = getFree();
        
        if(t.insert(key, addr) == false) {
            return false;
        }
        
        r.writeRow(addr);
        
        return true;
    }
    
    public boolean remove(int key) {
        /*
         * If a row witht the key is in the table it is removed and true is returned
         * otherwise false is returned.
         * 
         * The method must use the B+Tree to determine if a row with the key exists.
         * 
         * If the row is deleted the key must be deleted from the B+Tree.
         */
        return true;
    }
    
    public LinkedList<String> search(int key) throws IOException {
        /*
         * If a row with the key is found in the table return a list of the other fields in the row.
         * The String values in the list should not include the null characters.
         * 
         * If a row with the key is not found return an empty list.
         * 
         * The method must use the equality search in the B+Tree.
         */
        
        LinkedList<String> list = new LinkedList<>();
        long k = t.search(key);
        
        if(k == 0) {
            return list;
        }
        
        rows.seek(k);
        rows.readInt();
        
        for(int i = 0; i < numOtherFields; i++) {
            String s = "";
            for(int j = 0; j < otherFieldLengths[i]; j++) {
                char c = rows.readChar();
                if(c != '\0') {
                    s += c;
                }
            }
            list.add(s);
        }
        
        return list;
    }
    
    public LinkedList<LinkedList<String>> search(int low, int high) throws IOException {
        //PRE: low <= high
        /*
         * For each row with a key that is in the range low to high inclusive list
         * of the fields in the row is added to the list returned by the call.
         * 
         * If there are no rows with a key in the range return an empty list
         * 
         * The method must use the range search in B+Tree
         */
        
        /*
         * take LinkedList<long> returned by BTree search
         * search for each long, and put the data into a LinkedList<String>
         * Then put those LinkedList<String> into the LinkedList<LinkedList<String>>
         */
        LinkedList<LinkedList<String>> list = new LinkedList<>();
        
        LinkedList<Long> addrs = t.search(low, high);
        
        while(addrs.size() != 0) {
            LinkedList<String> str = new LinkedList<>();
            rows.seek(addrs.remove());
            rows.readInt();
            
            for(int i = 0; i < numOtherFields; i++) {
                String s = "";
                for(int j = 0; j < otherFieldLengths[i]; j++) {
                    char c = rows.readChar();
                    if(c != '\0') {
                        s += c;
                    }
                }
                str.add(s);
            }
            
            list.add(str);
        }
        return list;
    }
    
    public void print() throws IOException {
        //Print the rows to standard output in ascending order (based on the keys)
        //One row per line
        
        LinkedList<Long> addrs = t.search(Integer.MIN_VALUE, Integer.MAX_VALUE);
        
        while(addrs.size() != 0) {
            rows.seek(addrs.remove());
            System.out.print("Key:"+rows.readInt()+" Contents:");
            
            for(int i = 0; i < numOtherFields; i++) {
                for(int j = 0; j < otherFieldLengths[i]; j++) {
                    System.out.print(rows.readChar());
                }
                System.out.print(" ");
            }
            System.out.println();
        }
    }
    
    public void close() throws IOException {
        //closes the DBTable. The table should not be used after it is closed
        
        rows.seek(0);
        rows.writeInt(numOtherFields);
        for(int i = 0; i < numOtherFields; i++) {
            rows.writeInt(otherFieldLengths[i]);
        }
        rows.writeLong(free);
        t.close();
        rows.close();
    }
    
    private long getFree() throws IOException {
        long addr;
        
        if(free == 0) {
            addr = rows.length();
        } else {
            rows.seek(free);
            addr = free;
            rows.seek(addr);
            free = rows.readLong();
        }
        
        return addr;
    }
    
    public static void main(String[] args) throws IOException {
        //RandomAccessFile rows = new RandomAccessFile("test.txt", "rw");
        int[] i = {1, 1};
        DBTable table = new DBTable("test.txt", i, 60);
        
        char[][] arr = {{'J'},{'L'}};
        char[][] arr1 = {{'B'},{'L'}};
        char[][] arr2 = {{'E'},{'P'}};
        char[][] arr3 = {{'J'},{'M'}};
        char[][] arr4 = {{'D'},{'P'}};
        char[][] arr5 = {{'L'},{'T'}};
        char[][] arr6 = {{'A'},{'K'}};
        char[][] arr7 = {{'K'},{'L'}};
        char[][] arr8 = {{'B'},{'C'}};
        char[][] arr9 = {{'E'},{'J'}};
        char[][] arr10 = {{'B'},{'H'}};
        char[][] arr11 = {{'D'},{'S'}};
        char[][] arr12 = {{'T'},{'A'}};
        char[][] arr13 = {{'J'},{'Z'}};
        char[][] arr14 = {{'D'},{'F'}};
        table.insert(35, arr);
        table.insert(5, arr1);
        table.insert(10, arr2);
        table.insert(15, arr3);
        table.insert(20, arr4);
        table.insert(25, arr5);
        table.insert(30, arr6);
        table.insert(33, arr7);
        table.insert(100, arr8);
        table.insert(55, arr9);
        table.insert(60, arr10);
        table.insert(500, arr11);
        table.insert(66, arr12);
        table.insert(130, arr13);
        table.insert(200, arr14);
        
        table.search(35);
        table.search(5);
        table.search(10);
        table.search(15);
        table.search(20);
        table.search(25);
        table.search(30);
        table.search(33);
        table.search(100);
        table.search(55);
        table.search(60);
        table.search(500);
        table.search(66);
        table.search(130);
        table.search(200);
        table.close();
        table = new DBTable("test.txt");
        table.search(33, 200);
        LinkedList<LinkedList<String>> list = table.search(33, 200);
        int k = 0;
        System.out.println("Range search");
        while(k < list.size()) {
            LinkedList<String> x = list.get(k);
            int j = 0;
            while(j < x.size()) {
                System.out.println(x.get(0)+" "+x.get(1));
                j++;
            }
            k++;
        }
        
        table.print();
        table.close();
    }
}