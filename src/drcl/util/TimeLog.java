package drcl.util;

import java.util.ArrayList;

public class TimeLog {
	public static ArrayList<String> times = new ArrayList<String>();
	
	public static void add(String line){
		times.add(line);
	}
	
	public static void print(){
		for(String line : times){
			System.out.println(line);
		}
	}

	public static void clear() {
		times.clear();
	}
}
