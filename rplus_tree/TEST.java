package rplus_tree;

import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TEST {
	public static void main(String argv[]) throws IOException {

		TEST t = new TEST();
		// Case: test format_lakes
		RPlus<String> root = new RPlus<String>();

		BufferedReader br = new BufferedReader(new FileReader(
				"/Users/liwang/Desktop/format_lakes.txt"));
		String line = br.readLine();
		float[][] result;
		
		while (line != null){
			result = t.getMBR(line);
			root.insert(result[0], result[1], line);
			line = br.readLine();
			//System.out.println(num);
		}
		
		System.out.println(root.getAllLeaves().size());
		
		List<String> a_list = root.search(new float[]{100000,100000}, new float[]{200000,200000});
		System.out.println(a_list.size());
		
		// get object from line
	}

	public float[][] getMBR(String line) {
		if (line.contains("EMPTY"))
			return null;

		int pos1 = line.indexOf('(');
		int pos2 = line.indexOf(')');
		String data = line.substring(pos1 + 1, pos2);
		String delims = "[,| ]+";
		String[] strings = data.split(delims);
		if (((strings.length) & 1) == 1)
			return null;
		java.util.List<Float> nums = new java.util.ArrayList<Float>();
		for (int i = 0; i < strings.length; i++)
			nums.add(Float.parseFloat(strings[i]));

		int npoints = strings.length / 2;

		/*
		 * if (npoints == 1) { // Point object = new Point(nums.get(1),
		 * nums.get(2)); } else if (npoints == 4) { // Rectangle // suppose
		 * order is first --- second // | | // | | // fourth--- third float
		 * width = Math.abs(nums.get(2) - nums.get(0)); // x2-x1 float height =
		 * Math.abs(nums.get(1) - nums.get(3)); // y1-y4
		 * 
		 * object = new Rectangle(nums.get(1), nums.get(2), width, height); }
		 * else if (npoints >= 6) { // Polygon
		 */
		float[] xs = new float[npoints];
		float[] ys = new float[npoints];
		for (int i = 0; i < npoints; i++) {
			xs[i] = nums.get(2 * i);
			ys[i] = nums.get(2 * i + 1);
		}
		
		float low = Float.POSITIVE_INFINITY;
		float up = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < xs.length; i++) {
			if (xs[i] < low) {
				low = xs[i];
			}
			if (xs[i] > up) {
				up = xs[i];
			}
		}
		float lowerbound_x = low;
		float upperbound_x = up;

		low = Float.POSITIVE_INFINITY;
		up = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < ys.length; i++) {
			if (ys[i] < low) {
				low = ys[i];
			}
			if (ys[i] > up) {
				up = ys[i];
			}
		}
		float lowerbound_y = low;
		float upperbound_y = up;
		
		float width = upperbound_x - lowerbound_x;
		float height = upperbound_y - lowerbound_y;
		
		//System.out.println(lowerbound_x+" "+lowerbound_y+" "+upperbound_x+" "+upperbound_y);
		return new float[][]{new float[]{lowerbound_x, lowerbound_y}, new float[]{width, height}};
	}

}
