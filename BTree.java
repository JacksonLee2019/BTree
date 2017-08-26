// Jackson Lee

import java.io.*;
import java.util.*;

public class BTree {    
    RandomAccessFile f;
    int order;
    int blockSize;
    long root;
    long free;
    
    private class BTreeNode {
        private int count;
        private int[] keys = new int[order -1];
        private long[] children = new long[order];
        private long currentAddr;
        //constructors and other methods
        
        private BTreeNode(int c, int[] k, long[] ch, long addr) {
            count = c;
            keys = k;
            children = ch;
            currentAddr = addr;
        }
        
        private BTreeNode(long addr) throws IOException {
            //System.out.println("f is seeking:"+addr);
            f.seek(addr);
            
            count = f.readInt();
            
            for(int i = 0; i < keys.length; i++) {
                keys[i] = f.readInt();
            }
            
            for(int i = 0; i < children.length; i++) {
                children[i] = f.readLong();
            }
            
            currentAddr = addr;
        }
        
        private void writeNode(long addr) throws IOException {
            f.seek(addr);
            
            f.writeInt(count);
            
            for(int i = 0; i < keys.length; i++) {
                f.writeInt(keys[i]);
            }
            
            for(int i = 0; i < children.length; i++) {
                f.writeLong(children[i]);
            }
        }
    }
    
    public BTree(String filename, int bsize) throws IOException {
        //bsize is the block size. This value is used to calculate the order of the tree
        //all B+Tree nodes will use bsize bytes
        //make a new B+Tree
        
        order = bsize/12;
        blockSize = bsize;
        File path = new File(filename);
        
        if(path.exists()) {
            path.delete();
        }
        
        f = new RandomAccessFile(path, "rw");
        
        root = 0;
        free = 0;
        
        f.writeLong(root);
        f.writeLong(free);
        f.writeInt(blockSize);
    }
    
    public BTree(String filename) throws IOException {
        //open an existing B+Tree
        
        File path = new File(filename);
        
        f = new RandomAccessFile(path, "rw");
        
        f.seek(0);
        
        root = f.readLong();
        free = f.readLong();
        blockSize = f.readInt();
        order = blockSize/12;
    }
    
    private Stack<BTreeNode> path;
    private boolean split;
    private int val = 0;
    private long loc = 0;
    private int splitVal;
    
    public boolean insert(int key, long addr) throws IOException {
        /*
         * If key is not a duplicate add key to the B+Tree
         * addr is the address of the row that contains the key
         * return true if the key is added
         * return false if the key is a duplicate
         */
        
        BTreeNode newNode;
        split = true;
        
        if(root == 0) {
            root = getFree();
            newNode = new BTreeNode(0, new int[order - 1], new long[order], root);
            newNode.keys[0] = key;
            newNode.children[0] = addr;
            newNode.count--;
            newNode.writeNode(root);
            return true;
        }
        
        if(search(key) != 0) {
            return false;
        }
        
        BTreeNode n = path.pop();
        
        if(-n.count < n.keys.length) {
            simpleLeafInsert(key, addr, n);
        } else {        
            newNode = new BTreeNode(0, new int[order - 1], new long[order], getFree());
            
            splitLeafNode(n, newNode, key, addr);
            
            loc = newNode.currentAddr;;
            val = newNode.keys[0];
            
            n.children[order - 1] = newNode.currentAddr;
            n.writeNode(n.currentAddr);
            
            newNode.children[order - 1] = 0;
            newNode.writeNode(loc);
        }

        while(!path.empty() && split) {
            n = path.pop();          
            
            if(n.count < n.keys.length) {
                simpleNonLeafInsert(val, loc, n);
            } else {
                newNode = new BTreeNode(0, new int[order -1], new long[order], getFree());
                
                splitNode(n, newNode, val, loc);
                
                n.count = -n.count;
                n.writeNode(n.currentAddr);
                
                loc = newNode.currentAddr;
                val = splitVal;
                
                newNode.count = -newNode.count;
                
                newNode.writeNode(loc);
            }
        }
        
        if(split) {
            long oldRoot = root;
            root = getFree();
            
            BTreeNode newRoot = new BTreeNode(0, new int[order - 1], new long[order], root);
            
            newRoot.keys[0] = val;
            newRoot.children[0] = oldRoot;
            newRoot.children[1] = loc;
            newRoot.count++;
            newRoot.writeNode(root);
        }
                
        return true;
    }
    
    private void simpleLeafInsert(int key, long addr, BTreeNode n) throws IOException {
        int j = (-n.count) - 1;
        
        while(j >= 0 && n.keys[j] > key) {
            n.keys[j+1] = n.keys[j];
            n.children[j+1] = n.children[j];
            j--;
        }
        
        n.keys[j+1] = key;
        n.children[j+1] = addr;
        n.count--;
        n.writeNode(n.currentAddr);
        split = false;
    }
    
    private void simpleNonLeafInsert(int key, long addr, BTreeNode n) throws IOException {
        int j = n.count - 1;
        
        while(j >= 0 && n.keys[j] > key) {
            n.keys[j+1] = n.keys[j];
            n.children[j+2] = n.children[j+1];
            j--;
        }
        
        n.keys[j+1] = key;
        n.children[j+2] = addr;
        n.count++;
        n.writeNode(n.currentAddr);
        split = false;
    }
    
    private void splitLeafNode(BTreeNode n1, BTreeNode n2, int key, long addr) throws IOException {
        int[] tempKey = new int[order];
        long[] tempChildren = new long[order + 1];
        int n1Count = Math.abs(n1.count) - 1;
        
        for(int i = 0; i < n1.keys.length; i++) {
            tempKey[i] = n1.keys[i];
        }
        
        for(int i = 0; i < n1.children.length; i++) {
            tempChildren[i] = n1.children[i];
        }
        
        while(n1Count >= 0 && tempKey[n1Count] > key) {
            tempKey[n1Count+1] = tempKey[n1Count];
            tempChildren[n1Count+1] = tempChildren[n1Count];
            n1Count--;
        }
        
        tempKey[n1Count+1] = key;
        tempChildren[n1Count+1] = addr;
        
        n1.count = 0;
        n2.count = 0;
        
        for(int i = 0; i < tempKey.length; i++) {
            if(i < (tempKey.length/2)) {
                n1.keys[i] = tempKey[i];
                n1.children[i] = tempChildren[i];
                n1.count--;
            } else {
                n1.children[i] = 0;

                
                n2.keys[i - (tempKey.length/2)] = tempKey[i];
                n2.children[i - (tempKey.length/2)] = tempChildren[i];
                n2.count--;
            }
        }
    }
    
    private void splitNode(BTreeNode n1, BTreeNode n2, int key, long addr) throws IOException {
        int[] tempKey = new int[order];
        long[] tempChildren = new long[order + 1];
        int n1Count = Math.abs(n1.count) - 1;
        
        for(int i = 0; i < n1.keys.length; i++) {
            tempKey[i] = n1.keys[i];
        }
        
        for(int i = 0; i < n1.children.length; i++) {
            tempChildren[i] = n1.children[i];
        }
        
        while(n1Count >= 0 && tempKey[n1Count] > key) {
            tempKey[n1Count+1] = tempKey[n1Count];
            tempChildren[n1Count+1] = tempChildren[n1Count];
            n1Count--;
        }
        
        tempKey[n1Count+1] = key;
        tempChildren[n1Count+2] = addr;

        splitVal = tempKey[tempKey.length/2];
        
        n1.count = 0;
        n2.count = 0;
        int count = 0;
        
        for(int i = 0; i < tempKey.length - 1; i++) {
            if(tempKey[i] < splitVal) {
                n1.keys[i] = tempKey[i];
                n1.children[i] = tempChildren[i];
                n1.count--;
            } else {
                
                n2.keys[count] = tempKey[i + 1];
                n2.children[count] = tempChildren[i + 1];
                n2.count--;
                count++;
            }
        }
        
        n2.children[count] = tempChildren[order];
    }
    
    private long removeAddr;
    
    public long remove(int key) throws IOException{
        /*
         * If the key is in the B+Tree, remove the key and return the address of the row
         * return 0 if the key is not found in the tree
         */
        
        if(root == 0 || search(key) == 0) {
            return 0;
        }
        
        BTreeNode n = path.pop();
        
        if(-n.count - 1 > (order/2) - 1) {
            simpleRemove(key, n);
        } else {
            System.out.println("must borow or combine");
        }
        
        return 1;
    }
    
    private void simpleRemove(int key, BTreeNode n) {
        /*
         * set the key value to 0, and set removeAddr to the corresponding child value
         * reorganize the keys and children so there are no 0's inbetween
         */
        
        for(int i = 0; i < -n.count; i++) {
            if(i < key) {
                n.keys[i] = 0;
                removeAddr = n.children[i];
                n.children[i] = 0;
            }
        }
        
        for(int i = 0; i < -n.count; i++) {
            n.keys[i] = n.keys[i];
        }
    }
    
    public long search(int k) throws IOException {
        /*
         * This is an equality search
         * 
         * If the key is found return the address of the row with the key 
         * otherwise return 0 
         */
        
        path = new Stack<>();

        return search(k, root);        
    }
    
    private long search(int k, long addr) throws IOException {
        BTreeNode n = new BTreeNode(addr);
        path.push(n);
        int i;
        
        if(n.count <= 0) {
            for(i = 0; i < -n.count; i++) {
                if(k == n.keys[i]) {
                    return n.children[i];
                }
            }
            
            return 0;
        }
        
        for(i = 0; i < n.count; i++) {
            if(k < n.keys[i]) {
                //path.push(n);
                break;
            }
        }
        
        return search(k, n.children[i]);
    }
    
    public LinkedList<Long> search(int low, int high) throws IOException {
        //PRE: low <= high
        /*
         * This is a range search
         * 
         * return a list of row addresses for all keys in the range low to high inclusive
         * return an empty list if no keys are in the range
         */
        
        LinkedList<Long> list = new LinkedList<>();
        search(low);
        BTreeNode n = path.pop();
        long addr = n.currentAddr;
        
        while(addr != 0) {
            for(int i = 0; i < -n.count; i++) {
                if(low <= n.keys[i] && n.keys[i] <= high) {
                    list.add(n.children[i]);
                }
            }
            addr = n.children[order - 1];
            n = new BTreeNode(addr);
        }
        
        return list;
    }
    
    public void print() throws IOException {

        //print the B+Tree to standard output
        //print one node per line
        
        System.out.println(print(root));
    }
    
    private String print(long addr) throws IOException {
        String s = "";
        
        BTreeNode n = new BTreeNode(addr);
        
        if(n.count < 0) {
            s = "";      
            s += "Count:"+n.count+" Keys:";
        
            for(int i = 0; i < Math.abs(n.count); i++) {
                s += n.keys[i]+" ";
            }
        
            s += "Children:";
        
            for(int i = 0; i < n.children.length; i++) {  
                s += n.children[i]+" ";
                
                if(n.children[i] == 0 && i == n.children.length) {
                    s += n.children[i]+" ";
                }
            }
            
            return s;
        }
             
        s += "Count:"+n.count+" Keys:";
        
        for(int i = 0; i < n.count; i++) {
            s += n.keys[i]+" ";
        }
        
        s += "Children:";
        
        for(int i = 0; i < n.count + 1; i++) {
            s += n.children[i]+" ";
        }
        
        for(int i = 0; i < n.count + 1; i++) {
            s += "\n" + print(n.children[i]);
        }
        
        return s;
    }
   
    public void close() throws IOException {
        //close the B+Tree. The tree should not be accessed after close is called
        
        f.seek(0);
        f.writeLong(root);
        f.writeLong(free);
        f.writeInt(blockSize);
        f.close();
    }
    
    private long getFree() throws IOException {
        if(free == 0) {
            return f.length();
        } else {
            f.seek(free);
            long addr = free;
            f.seek(addr);
            free = f.readLong();
            return addr;
        }
    }
    
    private void addFree(long newFree, BTreeNode temp) throws IOException {
        long addr = free;
        long tempAddr = 0;
        
        while(addr != 0) {
            f.seek(addr);
            tempAddr = addr;
            addr = f.readLong();
        }
        
        f.seek(tempAddr);
        f.writeLong(newFree);
        f.seek(newFree);
        f.writeLong(0);
    }
    
    public static void main(String[] args) throws IOException {
        //RandomAccessFile f = new RandomAccessFile("testIndex.txt", "rw");
        /*f.writeLong(620);
        f.writeLong(320);
        f.writeInt(60);
        f.writeLong(0);
        f.seek(80);
        f.writeLong(440);
        f.seek(140);
        f.writeLong(20);
        f.seek(200);
        f.writeInt(-3);
        f.writeInt(30);
        f.writeInt(40);
        f.writeInt(50);
        f.writeInt(0);
        f.writeLong(568);
        f.writeLong(532);
        f.writeLong(20);
        f.writeLong(0);
        f.writeLong(500);
        f.writeLong(560);
        f.seek(320);
        f.writeLong(80);
        f.seek(380);
        f.writeInt(-2);
        f.writeInt(10);
        f.writeInt(20);
        f.writeInt(0);
        f.writeInt(0);
        f.writeLong(148);
        f.writeLong(340);
        f.writeLong(0);
        f.writeLong(0);
        f.writeLong(200);
        f.writeLong(260);
        f.seek(500);
        f.writeInt(-2);
        f.writeInt(60);
        f.writeInt(70);
        f.writeInt(0);
        f.writeInt(0);
        f.writeLong(212);
        f.writeLong(404);
        f.writeLong(0);
        f.writeLong(0);
        f.writeLong(0);
        f.writeLong(140);
        f.seek(620);
        f.writeInt(2);
        f.writeInt(30);
        f.writeInt(60);
        f.writeInt(0);
        f.writeInt(0);
        f.writeLong(380);
        f.writeLong(200);
        f.writeLong(500);
        f.writeLong(0);
        f.writeLong(0);*/
        //f.close();
        
        BTree t = new BTree("testIndex.txt", 60);
        
        System.out.println(t.insert(15, 15));
        System.out.println(t.insert(10, 10));
        System.out.println(t.insert(20, 20));
        System.out.println(t.insert(5, 5));
        System.out.println(t.insert(25, 25));
        System.out.println(t.insert(30, 30));
        System.out.println(t.insert(35, 35));
        System.out.println(t.insert(33, 33));
        System.out.println(t.insert(100, 100));
        System.out.println(t.insert(55, 55));
        System.out.println(t.insert(60, 60));
        System.out.println(t.insert(500, 500));
        System.out.println(t.insert(66, 66));       
        System.out.println(t.insert(130, 130));
        System.out.println(t.insert(200, 200));
        
        System.out.println(t.search(15));
        System.out.println(t.search(10));
        System.out.println(t.search(20));
        System.out.println(t.search(5));
        System.out.println(t.search(25));
        System.out.println(t.search(30));
        System.out.println(t.search(35));
        System.out.println(t.search(33));
        System.out.println(t.search(100));
        System.out.println(t.search(55));
        System.out.println(t.search(60));
        System.out.println(t.search(500));
        System.out.println(t.search(66));
        System.out.println(t.search(130));
        System.out.println(t.search(200));
        t.close();
        t = new BTree("testIndex.txt");
        System.out.println(t.search(5, 500));
        System.out.println(t.search(100, 500));
        System.out.println(t.search(2, 4));
        t.print();
        t.close();
    }
}