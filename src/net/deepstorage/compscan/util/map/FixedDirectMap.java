package net.deepstorage.compscan.util.map;

import java.util.*;
import java.lang.reflect.*;
import java.io.*;
import java.nio.*;
import net.deepstorage.compscan.util.Util;
import static net.deepstorage.compscan.util.MemUtil.*;

public class FixedDirectMap extends DataMap{
   private final ByteBuffer data;
   private int currOff;
   
   private final static Method getCleaner;
   private final static Method clean;
   static{
      try{
         ByteBuffer bb=ByteBuffer.allocateDirect(0);
         getCleaner=bb.getClass().getMethod("cleaner");
         clean=getCleaner.getReturnType().getMethod("clean");
         getCleaner.setAccessible(true);
         clean.setAccessible(true);
      }
      catch(Exception e){
         throw new Error(e);
      }
   }
   
//public int reads;
//public int writes;
//public int matches;
   
   public FixedDirectMap(
      int keySize, int valueSize, int addrSize, int maxListSize, int dataSize
   ){
      super(keySize, valueSize, addrSize, maxListSize);
      data=createBuffer(dataSize);
      data.order(ByteOrder.LITTLE_ENDIAN);
   }
   
   public void dispose(){
      dispose(data);
      currOff=0;
   }
   
   protected ByteBuffer createBuffer(int size){
      return ByteBuffer.allocateDirect(size);
   }
   
   protected void dispose(ByteBuffer bb){
      try{
         clean.invoke(getCleaner.invoke(bb));
      }
      catch(Exception e){
         System.out.println("WARNING: DirectMap: buffer disposal failed: "+e);
      }
   }
   
   protected long newBlock(int size, boolean clear){
      long v=currOff;
      currOff+=size;
      return v;
   }
   
   protected long read(long blockAddr, int off, int bytes){
//reads++;
      int p=(int)blockAddr+off;
      return data.getLong(p)&(-1L>>>((8-bytes)<<3));
   }
   
   protected int read(long blockAddr, int off, byte[] out, int outOff, int bytes){
//reads++;
      data.position((int)blockAddr+off);
      data.get(out,outOff,bytes);
      return bytes;
   }
   
   protected void write(long blockAddr, int off, long value, int bytes){
//writes++;
      int p=(int)blockAddr+off;
      long v=data.getLong(p);
      long loMask=-1L>>>((8-bytes)<<3);
      data.putLong(p,(v&~loMask)^(value&loMask));
   }
   
   protected void write(long blockAddr, int off, byte[] in, int inOff, int bytes){
//writes++;
      data.position((int)blockAddr+off);
      data.put(in,inOff,bytes);
   }
   
   protected boolean matches(long blockAddr, int off, byte[] key, int keyOff, int keySize){
//matches++;
      int p=(int)blockAddr+off;
      int shift=0;
      while((keySize-shift)>=8){
         if(readLong(key,keyOff+shift)!=data.getLong(p+shift)) return false;
         shift+=8;
      }
      if((keySize-shift)>=4){
         if(readInt(key,keyOff+shift)!=data.getInt(p+shift)) return false;
         shift+=4;
      }
      if((keySize-shift)>=2){
         if(readShort(key,keyOff+shift)!=data.getShort(p+shift)) return false;
         shift+=2;
      }
      if((keySize-shift)>=1){
         return key[keyOff+shift]==data.get(p+shift);
      }
      return true;
   }
   
   public long dataSize(){
      return currOff;
   }
   
   public String toString(boolean data, boolean structure){
      String s=
         "DirM(\n"+
         "  data size="+currOff+"\n"+
         ")"
      ;
      if(data){
         s+="{\n";
         s+=" "+Util.toString(this.data,0,currOff)+"\n";
         s+="}\n";
      }
      else s+="\n";
      s+=super.toString(structure);
      return s;
   }
   
   public String toString(){
      return toString(false,false);
   }
   
   public static void main(String[] args){
      System.out.println(getCleaner);
      System.out.println(clean);
      
      int dataSize=1<<29;
      FixedDirectMap map=new FixedDirectMap(4, 8, 5, 3, dataSize);
      map.open();
      
      byte[] key={1,2,3,4,5};
      while(map.dataSize()<dataSize) map.newBlock(1024,true);
      for(int i=40000;i-->0;) map.write(0,0,map.read(0,0,8)+1,8);
      for(int i=40000;i-->0;) map.matches(0,0,key,0,key.length);
      int mask=dataSize-1;
      int N=10_000_000;
      long t0=System.currentTimeMillis();
      long seed=1;
      for(int i=N;i-->0;){
         long addr=seed&mask;
         map.write(addr,0,map.read(addr,0,1)+1,1);
         seed*=3;
      }
      long dt=System.currentTimeMillis()-t0;
      System.out.println(N+" read+writes : "+dt+" mls");
      System.out.println((N*1000f/dt)+" read+writes/s");
      seed=1;
      t0=System.currentTimeMillis();
      for(int i=N;i-->0;){
         long addr=seed&mask;
         map.matches(addr,0,key,0,key.length);
         seed*=3;
      }
      dt=System.currentTimeMillis()-t0;
      System.out.println(N+" matches : "+dt+" mls");
      System.out.println((N*1000f/dt)+" matches/s");
      System.exit(0);
      
//      map.write(0,0,new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19},0,20);
//      System.out.println(Util.toString(map.data,0,70));
//      //System.out.println(map);
//      System.out.println(map.matches(0,0,new byte[]{0,1,2,3,4,5,6,7,8,9,10},0,11));//t
//      System.out.println(map.matches(0,1,new byte[]{0,1,2,3,4,5,6,7,8,9,10},0,11));//f
//      System.out.println(map.matches(0,1,new byte[]{1,2,3,4,5,6,7,8,9,10},0,10));//t
//      System.out.println(map.matches(0,5,new byte[]{5,6,7,8,9,10},0,6));//t
//      System.out.println(map.matches(0,6,new byte[]{5,6,7,8,9,10},0,6));//f
//      System.out.println();
//      System.out.println(Long.toString(map.read(1,0,4),16));//0x04030201
//      System.out.println(Long.toString(map.read(1,0,6),16));//0x060504030201
//      System.out.println(Long.toString(map.read(1,0,8),16));//0x0807060504030201
//      System.out.println(Long.toString(map.read(3,0,4),16));//0x06050403
//      System.out.println(Long.toString(map.read(3,0,6),16));//0x080706050403
//      System.out.println(Long.toString(map.read(3,0,8),16));//0x0a09080706050403
//      map.write(0,21,0x0807060504030201L,8);
//      map.write(0,30,0x0807060504030201L,7);
//      map.write(0,38,0x0807060504030201L,6);
//      map.write(0,45,0x0807060504030201L,5);
//      map.write(0,51,0x0807060504030201L,4);
//      map.write(0,56,0x0807060504030201L,3);
//      map.write(0,60,0x0807060504030201L,2);
//      map.write(0,63,0x0807060504030201L,1);
//      System.out.println(Util.toString(map.data,0,70));
//      System.exit(0);
      
      DataMap.Cursor c=map.new Cursor();
      System.out.println(c.put(new byte[]{1,2,1,4},0));
      System.out.println(c.put(new byte[]{1,2,1,4},0));
      System.out.println(c.put(new byte[]{1,3,1,4},0));
      System.out.println(c.put(new byte[]{1,3,1,5},0));
      System.out.println(c.put(new byte[]{1,3,1,6},0));
      System.out.println(c.put(new byte[]{1,3,1,7},0));
//      System.out.println("map="+map.toString(true,false));
      System.out.println("map="+map.toString(false,true));
      System.out.println(" ===  ");
      System.out.println(c.seek(new byte[]{1,2,1,4},0)); //true
      System.out.println(c.seek(new byte[]{1,2,1,5},0)); //false
      System.out.println(c.seek(new byte[]{1,2,3,4},0)); //false
      System.out.println(c.seek(new byte[]{1,3,1,3},0)); //false
      System.out.println(c.seek(new byte[]{1,3,1,4},0)); //true
      System.out.println(c.seek(new byte[]{1,3,1,5},0)); //true
      System.out.println(c.seek(new byte[]{1,3,1,6},0)); //true
      System.out.println(c.seek(new byte[]{1,3,1,7},0)); //true
      System.out.println(c.seek(new byte[]{1,3,1,8},0)); //false
      System.out.println(" ===  ");
      c.scan((k)->{
         System.out.println(Util.toString(k,0,k.length)+" -> "+c.readValue(0,8));
      });
   }
}
