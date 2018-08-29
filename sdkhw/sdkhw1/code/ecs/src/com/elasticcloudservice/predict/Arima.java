package com.elasticcloudservice.predict;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Arima
{
	double [] primaryData = {};
	double [] afterDiffData = {};
	ArmaMeans armaMeans = new ArmaMeans();
	double avgsumData;
	double stderrDara;
	
	
	List<double []>arimaCoe = new ArrayList<>();
	
	
	public Arima(double [] primaryData)
	{
//		double[] preZscoreProcess = new double[primaryData.length];
//		preZscoreProcess = this.getZscore(primaryData);
//		List<Double> tempPrimaryData = new ArrayList<Double>();
//		for(int i=0; i<preZscoreProcess.length; i++){
//			if(preZscoreProcess[i]>=3 || preZscoreProcess[i]<=-3){//对于噪声点使用前后平均值替代
//				//continue;
//				if(i<1){
//					primaryData[i]=(primaryData[i+1]+primaryData[i])/2;
//				}else if(i>primaryData.length-2){
//					primaryData[i]=(primaryData[i-1]+primaryData[i])/2;
//				}else{
//					primaryData[i]=(primaryData[i-1]+primaryData[i+1]+primaryData[i])/3;
//				}
//			}
//			tempPrimaryData.add(primaryData[i]);
//		}
//		
//		this.primaryData = new double[tempPrimaryData.size()];
//		for(int i=0; i<tempPrimaryData.size(); i++){
//			this.primaryData[i] = tempPrimaryData.get(i);
//			System.out.print(this.primaryData[i] + " ");
//		}
//		System.out.println();
		
		this.primaryData = primaryData;
	}
	
	
	/**
	 * 原始数据标准化处理：Z-Score归一化，得到Z分数
	 * @param 待处理数据
	 * @return 归一化过后的数据
	 */
//	public double[] getZscore(double[] primaryDada)
//	{
//		avgsumData=armaMeans.avgData(primaryDada);
//		stderrDara=armaMeans.calculateOfStandardVariance(primaryDada);
//		
//		double[] tempdata = new double[primaryDada.length];
//		
//		for(int i=0;i<primaryDada.length;i++)
//		{
//			tempdata[i]=(primaryDada[i]-avgsumData)/stderrDara;//数据减去平均值后再除以标准差
//		}
//		
//		return tempdata;
//	}
	
	public double [] firDiffOfPreProcess(double [] preData)		
	{	
		double [] tmpData = new double[preData.length - 1];
		for (int i = 0; i < preData.length - 1; ++i)
		{
			tmpData[i] = preData[i + 1] - preData[i];
		}
		return tmpData;
	}
	
	public double [] seasonDiffOfPreProcess(double [] preData)		
	{	
		double [] tmpData = new double[preData.length - 7];
		for (int i = 0; i < preData.length - 7; ++i)
		{
			tmpData[i] = preData[i + 7] - preData[i];
		}
		return tmpData;
	}
	
	public double [] preDealDiff(int period)
	{
		if (period >= primaryData.length - 1)		
		{
			period = 0;
		}
		switch (period)
		{
		case 0:
			return this.primaryData;
		case 1:		 
			this.afterDiffData = this.firDiffOfPreProcess(this.primaryData);
			return this.afterDiffData;
		default:	
			return seasonDiffOfPreProcess(primaryData);
		}
	}

	public int aftDeal(int predictValue, int period)
	{
		if (period >= primaryData.length)
		{
			period = 0;
		}
		
		switch (period)
		{
		case 0:
			return (int)predictValue;
		case 1:
			return (int)(predictValue + primaryData[primaryData.length - 1]);
		case 2:
		default:	
			return (int)(predictValue + primaryData[primaryData.length - 7]);
		}
	}
	
	public int [] getARIMAModel(int period, ArrayList<int []>notModel, boolean needNot)
	{
		double [] data = this.preDealDiff(period);
		
		double minAIC = Double.MAX_VALUE;
		int [] bestModel = new int[3];
		int type = 0;
		List<double []>coefficient = new ArrayList<>();
		
		// model产生, 即产生相应的p, q参数
		int len = data.length;
		if (len > 5)
		{
			len = 5;
		}
	//	int size = ((len + 2) * (len + 1)) / 2 - 1;
		int size = (len+1)*(len+1)-1;
		int [][] model = new int[size][2];
		int cnt = 0;
		for (int i = 0; i <= len; ++i)
		{
			for (int j = 0; j <= len; ++j)
			{
				if (i == 0 && j == 0)
					continue;
				model[cnt][0] = i;
				model[cnt][1] = j;
				cnt++;
			}
		}

		for (int i = 0; i < model.length; ++i)
		{
			// 控制选择的参数
			boolean token = false;
			if (needNot)
			{
				for (int k = 0; k < notModel.size(); ++k)
				{
					if (model[i][0] == notModel.get(k)[0] && model[i][1] == notModel.get(k)[1])
					{
						token = true;
						break;
					}
				}
			}
			if (token)
			{
				continue;
			}
			
			if (model[i][0] == 0)
			{
				Ma ma = new Ma(data, model[i][1]);
				coefficient = ma.calculateCoefficientOfMA();
				type = 1;
			}
			else if (model[i][1] == 0)
			{
				Ar ar = new Ar(data, model[i][0]);
				coefficient = ar.calculateCoefficientOfAR();
				type = 2;
			}
			else
			{
				Arma arma = new Arma(data, model[i][0], model[i][1]);
				coefficient = arma.calculateCoefficientOfARMA();
				type = 3;
			}				
			double aic = new ArmaMeans().getModelAIC(coefficient, data, type);
			// 在求解过程中如果阶数选取过长，可能会出现NAN或者无穷大的情况
			if (!Double.isInfinite(aic) && !Double.isNaN(aic) && aic < minAIC)
			{
				minAIC = aic;
				bestModel[0] = model[i][0];
				bestModel[1] = model[i][1];
				bestModel[2] = (int)Math.round(minAIC);
				this.arimaCoe = coefficient;
			}
		}
		return bestModel;
	}
	
	
	
	public int predictValue(int p, int q, int period)
	{
		double [] data = this.preDealDiff(period);
		int n = data.length;
		int predict = 0;
		double tmpAR = 0.0, tmpMA = 0.0;
		double [] errData = new double[q + 1];
		
		Random random = new Random();
		
		if (p == 0)
		{
			double [] maCoe = this.arimaCoe.get(0);
			for(int k = q; k < n; ++k)
			{
				tmpMA = 0;
				for(int i = 1; i <= q; ++i)
				{
					tmpMA += maCoe[i] * errData[i];
				}
				//产生各个时刻的噪声
				for(int j = q; j > 0; --j)
				{
					errData[j] = errData[j - 1];
				}
				errData[0] = random.nextGaussian()*Math.sqrt(maCoe[0]);
			}
			
			predict = (int)(tmpMA); //产生预测
		}
		else if (q == 0)
		{
			double [] arCoe = this.arimaCoe.get(0);
			
			for(int k = p; k < n; ++k)
			{
				tmpAR = 0;
				for(int i = 0; i < p; ++i)
				{
					tmpAR += arCoe[i] * data[k - i - 1];
				}
			}
			predict = (int)(tmpAR);
		}
		else
		{
			double [] arCoe = this.arimaCoe.get(0);
			double [] maCoe = this.arimaCoe.get(1);
			
			for(int k = p; k < n; ++k)
			{
				tmpAR = 0;
				tmpMA = 0;
				for(int i = 0; i < p; ++i)
				{
					tmpAR += arCoe[i] * data[k- i - 1];
				}
				for(int i = 1; i <= q; ++i)
				{
					tmpMA += maCoe[i] * errData[i];
				}
			
				//产生各个时刻的噪声
				for(int j = q; j > 0; --j)
				{
					errData[j] = errData[j-1];
				}
				
				errData[0] = random.nextGaussian() * Math.sqrt(maCoe[0]);
			}
			
			predict = (int)(tmpAR + tmpMA);
		}
		
		return predict;
	}
}
