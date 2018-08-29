package com.elasticcloudservice.predict;

import java.util.ArrayList;
import java.util.List;

public class Ar
{
	private double [] data;
	private int p;
	
	public Ar(double [] data, int p)
	{
		this.data = data;
		this.p = p;
	}
	
	public List<double []> calculateCoefficientOfAR()
	{
		List<double []>vec = new ArrayList<>();
		double [] coefficientOfAR = new ArmaMeans().computeARCoe(this.data, this.p);
		
		vec.add(coefficientOfAR);
		
		return vec;
	}
}
