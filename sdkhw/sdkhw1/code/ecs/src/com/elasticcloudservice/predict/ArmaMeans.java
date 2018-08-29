package com.elasticcloudservice.predict;

import java.util.List;
import java.util.Random;


public class ArmaMeans
{
	
	
	public ArmaMeans()
	{
		
	}

	
	public double avgData(double [] primaryData)
	{
		return this.sumData(primaryData) / primaryData.length;
	}
	
	
	public double sumData(double [] primaryData)
	{
		double sum = 0.0;
		
		for (int i = 0; i < primaryData.length; ++i)
		{
			sum += primaryData[i];
		}
		return sum;
	}
	
	
	public double calculateOfStandardVariance(double [] primaryData)
	{
		return Math.sqrt(this.calculateOfVariance(primaryData));
	}
	
	
	
	public double calculateOfVariance(double [] primaryData)
	{
		if (primaryData.length <= 1)
			return 0.0;
		
		double var = 0.0;
		double mu = this.avgData(primaryData);
		
		for (int i = 0; i < primaryData.length; ++i)
		{
			var += (primaryData[i] - mu) * (primaryData[i] - mu);
		}
		var /= (primaryData.length - 1);		//方差的无偏估计
		
		return var;
	}
	
	
	public double [] autoCorrData(double [] primaryData, int order)
	{
		double [] autoCov = this.autoCovData(primaryData, order);
		double [] autoCorr = new double[order + 1];		//默认初始化为0
		double var = this.calculateOfVariance(primaryData);
		
		if (var != 0)
		{
			for (int i = 0; i < autoCorr.length; ++i)
			{
				autoCorr[i] = autoCov[i] / var;
			}
		}
		
		return autoCorr;
	}
	
	
	public double mutalCorr(double [] dataFir, double [] dataSec)
	{
		double sumX = 0.0;
		double sumY = 0.0;
		double sumXY = 0.0;
		double sumXSq = 0.0;
		double sumYSq = 0.0;
		int len = 0;
		
		if (dataFir.length != dataSec.length)
		{
			len = Math.min(dataFir.length, dataSec.length);
		}
		else
		{
			len = dataFir.length;
		}
		for (int i = 0; i < len; ++i)
		{
			sumX += dataFir[i];
			sumY += dataSec[i];
			sumXY += dataFir[i] * dataSec[i];
			sumXSq += dataFir[i] * dataFir[i];
			sumYSq += dataSec[i] * dataSec[i];
		}		
		
		double numerator = sumXY - sumX * sumY / len;
		double denominator = Math.sqrt((sumXSq - sumX * sumX / len) * (sumYSq - sumY * sumY / len));
		
		if (denominator == 0)
		{
			return 0.0;
		}
		
		return numerator/ denominator;
	}
	
	
	public double [][] computeMutalCorrMatrix(double [][] data)
	{
		double [][] result = new double[data.length][data.length];
		for (int i = 0; i < data.length; ++i)
		{
			for (int j = 0; j < data.length; ++j)
			{
				result[i][j] = this.mutalCorr(data[i], data[j]);
			}
		}
		
		return result;
	}
	
	
	public double [] autoCovData(double [] primaryData, int order)
	{
		double mu = this.avgData(primaryData);
		double [] autoCov = new double[order + 1];
		
		for (int i = 0; i <= order; ++i)
		{
			autoCov[i] = 0.0;
			for (int j = 0; j < primaryData.length - i; ++j)
			{
				autoCov[i] += (primaryData[i + j] - mu) * (primaryData[j] - mu);
			}
			autoCov[i] /= (primaryData.length - i);
		}
		return autoCov;
	}
	
	
	public double getModelAIC(List<double []>vec, double [] data, int type)
	{
		int n = data.length;
		int p = 0, q = 0;
		double tmpAR = 0.0, tmpMA = 0.0;
		double sumErr = 0.0;
		Random random = new Random();
		
		/* MA */	
		if (type == 1)
		{
			double [] maCoe = vec.get(0);
			q = maCoe.length;
			double [] errData = new double[q];
			
			for (int i = q - 1; i < n; ++i)
			{
				tmpMA = 0.0;
				for (int j = 1; j < q; ++j)
				{
					tmpMA += maCoe[j] * errData[j];
				}
				
				for (int j = q - 1; j > 0; --j)
				{
					errData[j] = errData[j - 1];
				}
				errData[0] = random.nextGaussian() * Math.sqrt(maCoe[0]);
				sumErr += (data[i] - tmpMA) * (data[i] - tmpMA);
			}

			return (n - (q - 1)) * Math.log(sumErr / (n - (q - 1))) + (q + 1) * 2;//最大似然估计

		}
		/* AR */
		else if (type == 2)
		{
			double [] arCoe = vec.get(0);
			p = arCoe.length;
			
			for (int i = p - 1; i < n; ++i)
			{
				tmpAR = 0.0;
				for (int j = 0; j < p - 1; ++j)
				{
					tmpAR += arCoe[j] * data[i - j - 1];
				}
				sumErr += (data[i] - tmpAR) * (data[i] - tmpAR);
			}

			return (n - (p - 1)) * Math.log(sumErr / (n - (p - 1))) + (p + 1) * 2;// 最大似然估计

		}
		/* ARMA */
		else
		{
			double [] arCoe = vec.get(0);
			double [] maCoe = vec.get(1);
			p = arCoe.length;
			q = maCoe.length;
			double [] errData = new double[q];
			
			for (int i = p - 1; i < n; ++i)
			{
				tmpAR = 0.0;
				for (int j = 0; j < p - 1; ++j)
				{
					tmpAR += arCoe[j] * data[i - j - 1];
				}
				tmpMA = 0.0;
				for (int j = 1; j < q; ++j)
				{
					tmpMA += maCoe[j] * errData[j];
				}
				
				for (int j = q - 1; j > 0; --j)
				{
					errData[j] = errData[j - 1];
				}
				errData[0] = random.nextGaussian() * Math.sqrt(maCoe[0]);
				
				sumErr += (data[i] - tmpAR - tmpMA) * (data[i] - tmpAR - tmpMA);
			}

			return (n - (q + p - 1)) * Math.log(sumErr / (n - (q + p - 1))) + (p + q) * 2;//最大似然估计
			
		}
	}

	
	public double [][] LevinsonSolve(double [] garma)
	{
		int order = garma.length - 1;
		double [][] result = new double[order + 1][order + 1];
		double [] sigmaSq = new double[order + 1];
		
		sigmaSq[0] = garma[0];
		result[1][1] = garma[1] / sigmaSq[0];
		sigmaSq[1] = sigmaSq[0] * (1.0 - result[1][1] * result[1][1]);
		for (int k = 1; k < order; ++k)
		{
			double sumTop = 0.0;
			double sumSub = 0.0;
			for (int j = 1; j <= k; ++j)
			{
				sumTop += garma[k + 1 - j] * result[k][j];
				sumSub += garma[j] * result[k][j];
			}
			result[k + 1][k + 1] = (garma[k + 1] - sumTop) / (garma[0] - sumSub);
			for (int j = 1; j <= k; ++j)
			{
				result[k + 1][j] = result[k][j] - result[k + 1][k + 1] * result[k][k + 1 - j];
			} 
			sigmaSq[k + 1] = sigmaSq[k] * (1.0 - result[k + 1][k + 1] * result[k + 1][k + 1]);
		}
		result[0] = sigmaSq;
		
		return result;
	}

	
	public double [] computeARCoe(double [] primaryData, int p)
	{
		double [] garma = this.autoCovData(primaryData, p);		//p+1
		
		double [][] result = this.LevinsonSolve(garma);		//(p + 1) * (p + 1)
		double [] ARCoe = new double[p + 1];
		for (int i = 0; i < p; ++i)
		{
			ARCoe[i] = result[p][i + 1];
		}
		ARCoe[p] = result[0][p];		//噪声参数
		

		return ARCoe;
	}

	
	public double [] computeMACoe(double [] primaryData, int q)
	{
		
		
		int p = (int)Math.log(primaryData.length);
		

		double [] bestGarma = this.autoCovData(primaryData, p);
		double [][] bestResult = this.LevinsonSolve(bestGarma);
		
		double [] alpha = new double[p + 1];
		alpha[0] = -1;
		for (int i = 1; i <= p; ++i)
		{
			alpha[i] = bestResult[p][i];
		}


		double [] paraGarma = new double[q + 1];
		for (int k = 0; k <= q; ++k)
		{
			double sum = 0.0;
			for (int j = 0; j <= p - k; ++j)
			{
				sum += alpha[j] * alpha[k + j];
			}
			paraGarma[k] = sum / bestResult[0][p];
		}
		
		double [][] tmp = this.LevinsonSolve(paraGarma);
		double [] MACoe = new double[q + 1];
		for (int i = 1; i < MACoe.length; ++i)
		{
			MACoe[i] = -tmp[q][i];
		}
		MACoe[0] = 1 / tmp[0][q];		//噪声参数
		

		
		return MACoe;
	}

	
	
	
	public double [] computeARMACoe(double [] primaryData, int p, int q)
	{
		double [] allGarma = this.autoCovData(primaryData, p + q);
		double [] garma = new double[p + 1];
		for (int i = 0; i < garma.length; ++i)
		{
			garma[i] = allGarma[q + i];
		}
		double [][] arResult = this.LevinsonSolve(garma);
		
		// AR
		double [] ARCoe = new double[p + 1];
		for (int i = 0; i < p; ++i)
		{
			ARCoe[i] = arResult[p][i + 1];
		}
		ARCoe[p] = arResult[0][p];

		
		// MA
		double [] alpha = new double[p + 1];
		alpha[0] = -1;
		for (int i = 1; i <= p; ++i)
		{
			alpha[i] = ARCoe[i - 1];
		}
		
		double [] paraGarma = new double[q + 1];
		for (int k = 0; k <= q; ++k)
		{
			double sum = 0.0;
			for (int i = 0; i <= p; ++i)
			{
				for (int j = 0; j <= p; ++j)
				{
					sum += alpha[i] * alpha[j] * allGarma[Math.abs(k + i - j)];
				}
			}
			paraGarma[k] = sum;
		}
		double [][] maResult = this.LevinsonSolve(paraGarma);
		double [] MACoe = new double[q + 1];
		for (int i = 1; i <= q; ++i)
		{
			MACoe[i] = maResult[q][i];
		}
		MACoe[0] = maResult[0][q];
		

		
		double [] ARMACoe = new double[p + q + 2];
		for (int i = 0; i < ARMACoe.length; ++i)
		{
			if (i < ARCoe.length)
			{
				ARMACoe[i] = ARCoe[i];
			}
			else
			{
				ARMACoe[i] = MACoe[i - ARCoe.length];
			}
		}
		return ARMACoe;
	}
}
