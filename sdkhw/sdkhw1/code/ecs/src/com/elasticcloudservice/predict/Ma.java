package com.elasticcloudservice.predict;

import java.util.ArrayList;
import java.util.List;


public class Ma
{
	private double [] data;
	private int q;
	
	public Ma(double [] data, int q)
	{
		this.data = data;
		this.q = q;
	}
	
	public List<double []> calculateCoefficientOfMA()
	{
		List<double []>vec = new ArrayList<>();
		double [] coefficientOfMA = new ArmaMeans().computeMACoe(this.data, this.q);
		
		vec.add(coefficientOfMA);
		
		return vec;
	}
}
