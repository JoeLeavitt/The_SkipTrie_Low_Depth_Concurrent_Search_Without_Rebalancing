/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package concurrentskip;
/**
 *
 * @author haroldmarcial
 */


public class SkipTrie {
    private final ConcurrentSkipListMap skipList;
    private final LockFreeHashSet prefixes;
    
    private final int TOP = 5;
    
    
    public SkipTrie(){
        skipList = new ConcurrentSkipListMap();
        prefixes = new LockFreeHashSet<Integer,TrieNode>((int)Math.pow(2,20));
        prefixes.add("", new TrieNode(""));
    }
    
    
    public class TrieNode{
        String key;
        ConcurrentSkipListMap.Index<Integer, Boolean> pointers[];
        
        public TrieNode(String key){
            this.key = key;
            this.pointers = new ConcurrentSkipListMap.Index[2];
            this.pointers[0] = null;
            this.pointers[1] = null;
        }
        
    }
    
    public ConcurrentSkipListMap.Index<Integer, Boolean> xFastTriePred(int key){
        
        ConcurrentSkipListMap.Index<Integer, Boolean> curr = lowestAncestor(key);
        
        if(curr != null){
            while(getValidValue(curr) > key){
                if (curr.node.value == null){
                    curr = curr.prev;
                }
                else{
                    curr = curr.right;
                }
            }
        }
        
        return curr;
    }
    
    public ConcurrentSkipListMap.Index<Integer, Boolean> predecessor(int key){
        return skipList.skipListPred(key, xFastTriePred(key));
    }
    
    public ConcurrentSkipListMap.Index<Integer, Boolean> lowestAncestor(int key){
        
        String binaryString = String.format("%32s", Integer.toBinaryString(key)).replace(' ', '0');
        String common_prefix = "";
        ConcurrentSkipListMap.Index<Integer, Boolean> ancestor;
        
        // Find best ancestor from top node
        TrieNode tn = (TrieNode) prefixes.lookup(common_prefix);
        ancestor = tn.pointers[Character.getNumericValue(binaryString.charAt(31))];
        
        int start = 0;// The index of the first bit in the search window
        int size = 16; // The size of the search window
        while (size > 0){  
           String query = binaryString.substring(start, start + size - 1);
           int direction = Character.getNumericValue(binaryString.charAt(start + 1));
           
           TrieNode query_node = (TrieNode) prefixes.lookup(query);
           if(query_node != null){
               ConcurrentSkipListMap.Index<Integer, Boolean> candidate = query_node.pointers[direction];
               if(candidate != null){
                   if(Math.abs(key - getValidValue(candidate)) <= Math.abs(key - getValidValue(ancestor))){
                       ancestor = candidate;
                   }
                   common_prefix = query;
                   start = start + size;
               }
           }
           size = size / 2;
        }
        if (ancestor != null) System.out.println("FOUND AN ANCESTOR! " + ancestor); /// Testing
        return ancestor;
    }

    public boolean insert(int key){

        ConcurrentSkipListMap.Index<Integer, Boolean> pred = this.xFastTriePred(key);
        
        if(pred != null){
            if (getValidValue(pred) == key){
                return false;
            }
        }
        ConcurrentSkipListMap.Index<Integer, Boolean> node = skipList.topLevelInsert(key, pred);
        if (node == null)
            return false;
        if (node.node.orig_height != TOP){
            return true;
        }
        
        String binaryString = String.format("%32s", Integer.toBinaryString(key)).replace(' ', '0');
 
        for(int i = 0; i < binaryString.length(); i++){
            
            
            String p = binaryString.substring(0, binaryString.length()- 1 - i);
            int direction = Character.getNumericValue(binaryString.charAt(binaryString.length()- 1 - i));
            
            
            while (node.node.value != null){
               TrieNode tn = (TrieNode)  prefixes.lookup(p);
               
               if(tn == null){
                    tn = new TrieNode(p);
                    tn.pointers[direction] = node ;
                    if(prefixes.add(p, tn))
                        break;
               }
               else if (tn.pointers[0] == null && tn.pointers[1] == null){
                    prefixes.compareAndDelete(p, tn);
               }
               else{
                   ConcurrentSkipListMap.Index<Integer, Boolean> curr = tn.pointers[direction];
                   if(curr != null &&(direction == 0 && curr.node.key >= key)||(direction ==1 && curr.node.key<=key))
                       break;
                   
                   ConcurrentSkipListMap.Index<Integer, Boolean> next = node.right;
                   if(tn.pointers[direction].casPrev(curr, node))
                       break;
               }
               
            }
        }
        return true;
        
    }
        
    public boolean delete(int key){
        /*
            First delete from skiplist
            If it was a top level node then
                delete from x-fast trie as well
         */
        ConcurrentSkipListMap.Index<Integer,Boolean> pred = predecessor(key-1);
        ConcurrentSkipListMap.Pair<ConcurrentSkipListMap.Index<Integer,Boolean>, 
                ConcurrentSkipListMap.Index<Integer,Boolean>> pair = skipList.listSearch(key, pred);
        if(pair.right.node.orig_height != TOP){
            return skipList.remove(key, Boolean.TRUE);
        }
        if(!skipList.topLevelDelete(pair.left, pair.right))
            return true;
        String binaryString = String.format("%32s", Integer.toBinaryString(key)).replace(' ', '0');
 
        for(int i = 0; i < binaryString.length(); i++){
            
            String p = binaryString.substring(0, binaryString.length()- 1 - i);
            int direction = Character.getNumericValue(binaryString.charAt(binaryString.length()- 1 - i));
            
            TrieNode tn = (TrieNode)  prefixes.lookup(p);
            
            if(tn == null){
                continue;
            }
            ConcurrentSkipListMap.Index<Integer,Boolean> curr = tn.pointers[direction];
            
            while(curr == pair.right){
                ConcurrentSkipListMap.Pair<ConcurrentSkipListMap.Index<Integer,Boolean>, 
                ConcurrentSkipListMap.Index<Integer,Boolean>> pair2 = skipList.listSearch(key, pair.left);
                if(direction == 0){
                    tn.pointers[direction].casRight(curr, pair2.left);
                }
                else{
                    tn.pointers[direction].casRight(curr, pair2.right);
                }
                curr = tn.pointers[direction];
            }
            if(!((p.length() < binaryString.length()) || p.equals(binaryString))){
                tn.pointers[direction].casRight(curr, null);
            }
            if (tn.pointers[0] == null && tn.pointers[1] == null){
                    prefixes.compareAndDelete(p, tn);
            }
        }
        return true;
    }
    
    public Integer getValidValue(ConcurrentSkipListMap.Index<Integer,Boolean> index){
        if(index.node != null){
            System.out.println("[getValidValue] value found: " + index.node.key);
            return index.node.key;
        }
        else{
            return Integer.MIN_VALUE;
        }
    }
    


   
}