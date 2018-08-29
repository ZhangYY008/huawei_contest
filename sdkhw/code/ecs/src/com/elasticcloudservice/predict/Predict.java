package com.elasticcloudservice.predict;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Predict {

	static List<String> flavorsInfo = new ArrayList<String>();
	static List<Integer> cpusInfo = new ArrayList<Integer>();
	static List<Integer> memsInfo = new ArrayList<Integer>();

	public static int serverCpu, serverMem;
	public static int flagOptimization = 0;
	public static int daySpanOfinfo, daySpanOfPredict, beginTimeOfPredict,endTimeOfPredict;
	public static int oneDay = 3600 * 24;
	public static String cpuOrmem, predictBeginDate, predictEndDate;
	public static List<int[]> sequences = new ArrayList<int[]>();
	public static List<Integer> resultsAtLast = new ArrayList<Integer>();

	// 虚拟机
	static class Flavor{
		String name;
		int mem;
		int cpu;
		Flavor(String name, int mem, int cpu){
			this.name = name;
			this.mem = mem;
			this.cpu = cpu;
		}
	}
	
	// 物理服务器
	static class Deploy {
		public List<Flavor> vflavors;//已放置的虚拟机
		public int cpuLeft;
		public int memLeft;

		public Deploy() {
			vflavors = new ArrayList<>();
			cpuLeft = serverCpu;
			memLeft = serverMem;
		}
		
		// 放置虚拟机
		public boolean putFlavor(Flavor flavor){
			if(cpuLeft >= flavor.cpu && memLeft >= flavor.mem){
				cpuLeft -= flavor.cpu;
				memLeft -= flavor.mem;
				vflavors.add(flavor);
				return true;
			}
			return false;
		}
		// 获得cpu的利用率
		public double getCpuUsingRatio(){
			return 1-cpuLeft*1.0/serverCpu;
		}
		// 获得mem的利用率
		public double getMemUsingRatio(){
			return 1-memLeft*1.0/serverMem;
		}
	}

	public static String[] predictVm(String[] ecsContent, String[] inputContent) {

		String[] results = new String[ecsContent.length];
		String[] ecs = new String[ecsContent.length];

		List<String> history = new ArrayList<String>();

		for (int i = 0; i < ecsContent.length; i++) {
			ecs[i] = ecsContent[i].replaceAll("\\s{1,}", " ");// 把连续空格替换为单个空格

			if (ecs[i].split(" ").length == 4) {

				String[] array = ecs[i].split(" ");
				String flavorName = array[1];
				String createTime1 = array[2];
				String createTime2 = array[3];

				history.add(flavorName + " " + createTime1 + " "
						+ createTime2.replace(":", "-"));
			}
		}

		for (int i = 0; i < history.size(); i++) {
			results[i] = history.get(i);
			System.out.println(results[i]);
		}

		// 设置输入信息
		setInputInfo(inputContent);
		// 设置训练数据
		setData(history);
		// 进行预测
		List<Integer> resultsAtLat1 = predictImpl();
		// 部署虚拟机
		List<Deploy> serverDeployBest = new ArrayList<>();
		allocateFlavor2(resultsAtLat1, serverDeployBest);
		
//		System.out.println(serverDeployBest);
		//整理输出格式并对最后一个物理服务器进行简单调整
		results = settleOutput(serverDeployBest, resultsAtLat1);
		// 部署结果分析
		resultsAnalyze(results, serverDeployBest);
		return results;
	}

	
	public static void resultsAnalyze(String[] res, List<Deploy> serverDeployBest){
		
		int numOfPhysicalSever = Integer.parseInt(res[flavorsInfo.size() + 2]);//物理服务器的个数
		System.out.println(numOfPhysicalSever);
		String[] physicalSever = new String[numOfPhysicalSever];
		for(int i=0; i<numOfPhysicalSever; i++){
			physicalSever[i] = res[flavorsInfo.size() + 3 + i];
		}
		
		int sumOfFlavorCpuOfAll = 0;// 虚拟机总的cpu使用
		int sumOfFlavorMemOfAll = 0;// 虚拟机总的mem使用
		
	
		
		for(int i=0; i<numOfPhysicalSever; i++){
			String[] deployFlavor = physicalSever[i].split(" ");
			System.out.print(deployFlavor[0]+"  ");
			
			int sumOfFlavorCpu = 0;// 统计所用虚拟机的总cpu
			int sumOfFlavorMem = 0;// 统计所用虚拟机的总mem
			for(int j=1; j<deployFlavor.length; j++){
				if(j%2 != 0){
					int locationOfFlavor = flavorsInfo.indexOf(deployFlavor[j]);
					int numberOfFlavor = Integer.parseInt(deployFlavor[++j]);
					sumOfFlavorCpu += cpusInfo.get(locationOfFlavor)*numberOfFlavor;
					sumOfFlavorMem += memsInfo.get(locationOfFlavor)*numberOfFlavor;
				}	
			}
			sumOfFlavorCpuOfAll += sumOfFlavorCpu;
			sumOfFlavorMemOfAll += sumOfFlavorMem;
			
			System.out.println("物理服务器cpu数:" + serverCpu+" 使用的总cpu数:" + sumOfFlavorCpu+
					" cpu利用率:" + String.format("%.2f", (sumOfFlavorCpu)/(serverCpu*1.0)) + "     "+
					" 物理服务器mem数:" + serverMem+ " 使用的总mem数:" + sumOfFlavorMem +
					" mem利用率:" +  String.format("%.2f", (sumOfFlavorMem)/(serverMem*1.0)));
			
			
		}
		
		double cpuUsingRatio = sumOfFlavorCpuOfAll/(numOfPhysicalSever*serverCpu*1.0);
		double memUsingRatio = sumOfFlavorMemOfAll/(numOfPhysicalSever*serverMem*1.0);
		System.out.println("cpu总利用率：" + String.format("%.2f",cpuUsingRatio)
				+ "   mem总利用率：" + String.format("%.2f",memUsingRatio));
		
	}
	
	public static List<Integer> predictImpl() {
		

		// double[][] data = new double[sequences.size()][];
		// for(int i=0; i<flavorsInfo.size(); i++){
		// data[i] = new double[sequences.get(i).length];
		// for(int j=0; j<sequences.get(i).length; j++){
		// data[i][j] = sequences.get(i)[j];
		// }
		// }
/*
 * 
 * 
 * 按照每天来进行预测，所得的分数为82.604分
 */	
		List<List<Double>> data = new ArrayList<List<Double>>();
		for (int i = 0; i < flavorsInfo.size(); i++) {
			data.add(new ArrayList<Double>());
			for (int j = 0; j < sequences.get(i).length; j++) {
				data.get(i).add((double) sequences.get(i)[j]);
			}
		}

		for (int i = 0; i < sequences.size(); i++) {
			for (int y = 0; y < daySpanOfPredict; y++) {
				double[] dataTemp = new double[data.get(i).size()];

				for (int x = 0; x < data.get(i).size(); x++) {
					dataTemp[x] = data.get(i).get(x);
				}

				Arima arima = new Arima(dataTemp);

				ArrayList<int[]> list = new ArrayList<>();
				int period = daySpanOfPredict;
				int modelCnt = 5, cnt = 0; // 通过多次预测的平均数作为预测值
				int[] tmpPredict = new int[modelCnt];
				for (int k = 0; k < modelCnt; ++k) // 控制通过多少组参数进行计算最终的结果
				{
					int[] bestModel = arima.getARIMAModel(period, list,
							(k == 0) ? false : true);
					for (int q = 0; q < bestModel.length; q++) {
						// System.out.print(bestModel[q] + "   ");
					}
					if (bestModel.length == 0) {
						tmpPredict[k] = (int) dataTemp[dataTemp.length - period];
						cnt++;
						break;
					} else {
						// System.out.println("进入else");
						int predictDiff = arima.predictValue(bestModel[0],
								bestModel[1], period);
						tmpPredict[k] = arima.aftDeal(predictDiff, period);
						// System.out.println("预测值： "+tmpPredict[k]);
						cnt++;
					}
					// System.out.println("BestModel is " + bestModel[0] + " " +
					// bestModel[1]);
					list.add(bestModel);
				}

				double sumPredictTemp = 0.0;
				for (int k = 0; k < cnt; ++k) {
					sumPredictTemp += (double) tmpPredict[k] / (double) cnt;
				}
				int sumPredictTempI = (int) Math.round(sumPredictTemp);
				// System.out.println("Predict value="+sumPredictTempI);
				if (sumPredictTempI <= 0) {
					sumPredictTempI = 0;
				}
				data.get(i).add((double) sumPredictTempI);

			}
			int s = 0;
			for (int w = 0; w < daySpanOfPredict; w++) {
				s += data.get(i).get(data.get(i).size() - 1 - w);
			}
			resultsAtLast.add(s);
		}
		
		for (int i = 0; i < flavorsInfo.size(); i++) {
			System.out.println(flavorsInfo.get(i) + ":" + resultsAtLast.get(i));
		}

		return resultsAtLast;

		
	
		
/*	
 * 
 * 按照7天的总和来推算的，分数是79.91分
 * 	
		 List<List<Integer>> daySpanOfSequences = new ArrayList<List<Integer>>();
		 for(int i=0; i<sequences.size(); i++){
			 List<Integer> daySpanOfSequence = new ArrayList<Integer>();
			 for(int j=0; j<sequences.get(i).length-daySpanOfPredict+1; j++){
				 int AccumulateTemp=0;
			 	 for(int x=j; x<daySpanOfPredict+j; x++){
			 			AccumulateTemp += sequences.get(i)[x];
			 	 }
			 	 daySpanOfSequence.add(AccumulateTemp);
			 }
		
			 daySpanOfSequences.add(daySpanOfSequence);
		 }
		
		 System.out.println("daySpanOfSequences:");
		 for(int i=0; i<daySpanOfSequences.size(); i++){
			 System.out.print(flavorsInfo.get(i) + ":");
			 for(int j=0; j<daySpanOfSequences.get(i).size(); j++){
				 System.out.print(daySpanOfSequences.get(i).get(j) + " ");
			 }
			 System.out.println();
		 }
		
		 for(int i=0; i<daySpanOfSequences.size(); i++){
			 List<Integer> temp = daySpanOfSequences.get(i);
			 for(int j=0; j<daySpanOfPredict; j++){
				 int resultOfPredict = temp.get(temp.size()-1);
				 temp.add(resultOfPredict);
			 }
			 resultsAtLast.add(temp.get(temp.size()-1));
		 }
*/	 
/*			 
		 List<List<Integer>> daySpanOfSequences = new ArrayList<List<Integer>>();
		 for(int i=0; i<sequences.size(); i++){
			 List<Integer> daySpanOfSequence = new ArrayList<Integer>();
			 for(int j=0; j<sequences.get(i).length-daySpanOfPredict+1; j++){
				 int AccumulateTemp=0;
			 	 for(int x=j; x<daySpanOfPredict+j; x++){
			 			AccumulateTemp += sequences.get(i)[x];
			 	 }
			 	 daySpanOfSequence.add(AccumulateTemp);
			 }
		
			 daySpanOfSequences.add(daySpanOfSequence);
		 }
		
		 System.out.println("daySpanOfSequences:");
		 for(int i=0; i<daySpanOfSequences.size(); i++){
			 System.out.print(flavorsInfo.get(i) + ":");
			 for(int j=0; j<daySpanOfSequences.get(i).size(); j++){
				 System.out.print(daySpanOfSequences.get(i).get(j) + " ");
			 }
			 System.out.println();
		 }
		
		 List<List<Double>> data = new ArrayList<List<Double>>();
			for (int i = 0; i < daySpanOfSequences.size(); i++) {
				data.add(new ArrayList<Double>());
				for (int j = 0; j < daySpanOfSequences.get(i).size(); j++) {
					data.get(i).add((double) daySpanOfSequences.get(i).get(j));
				}
			}
		
			double[][] predictOf5 = new double[sequences.size()][5];
			
			for (int i = 0; i < sequences.size(); i++) {
				for(int j=0; j < 5; j++){
					for (int y = 0; y < daySpanOfPredict; y++) {
		//				System.out.println("每次序列的长度：" + data.get(i).size());
						double[] dataTemp = new double[data.get(i).size()];
						

						for (int x = 0; x < data.get(i).size(); x++) {
							dataTemp[x] = data.get(i).get(x);
							System.out.print(dataTemp[x]+" ");
						}	
						System.out.println();
						Arima arima = new Arima(dataTemp);

						ArrayList<int[]> list = new ArrayList<>();
						int period = daySpanOfPredict;
						int modelCnt = 5, cnt = 0; 
						int[] tmpPredict = new int[modelCnt];
						for (int k = 0; k < modelCnt; ++k) 
						{
							int[] bestModel = arima.getARIMAModel(1, list, (k == 0) ? false : true);
							for (int q = 0; q < bestModel.length; q++) {
								// System.out.print(bestModel[q] + "   ");
							}
							if (bestModel.length == 0) {
								tmpPredict[k] = (int) dataTemp[dataTemp.length - period];
								cnt++;
								break;
							} else {
								// System.out.println("进入else");
								int predictDiff = arima.predictValue(bestModel[0],
										bestModel[1], period);
								tmpPredict[k] = arima.aftDeal(predictDiff, period);
							 //   System.out.println("预测值： "+tmpPredict[k]);
								cnt++;
							}
	//						 System.out.println("BestModel is " + bestModel[0] + " " + bestModel[1]);
							list.add(bestModel);
						}

						double sumPredictTemp = 0.0;
						for (int k = 0; k < cnt; ++k) {
							sumPredictTemp += (double) tmpPredict[k] / (double) cnt;
						}
						int sumPredictTempI = (int) Math.round(sumPredictTemp);
						System.out.println("Predict value="+sumPredictTempI);
//						if (sumPredictTempI <= 0) {
//							sumPredictTempI = 0;
//						}
						data.get(i).add((double) sumPredictTempI);

					}
					Double s = data.get(i).get(data.get(i).size()-1);
					if(s <=0){
						s =(double) 0;
					}
			//		System.out.println("第" + j +"次预测：" + s);
//					String s1 = String.valueOf(s);	
//					String s2 = s1.substring(0, s1.indexOf("."));
//					int s3 = Integer.parseInt(s2);
//					System.out.println(s3);
					predictOf5[i][j] = s;
					int indexOfRemove = data.get(i).size()-1;
					for(int w=0; w<daySpanOfPredict; w++){
						data.get(i).remove(indexOfRemove-w);//删除最后7次预测值，重新再预测
					}	
				}
				double sumOfPredict5 = 0;
				double maxIndex = predictOf5[i][0];//定义最大值为该数组的第一个数
		        double minIndex = predictOf5[i][0];//定义最小值为该数组的第一个数
		        //遍历循环数组
		        for (int a = 0; a < predictOf5[i].length; a++) {
		            if(maxIndex < predictOf5[i][a]){
		                maxIndex = predictOf5[i][a];
		            }
		            if(minIndex > predictOf5[i][a]){
		                minIndex = predictOf5[i][a];
		            }
		        }
		        int flagOfNumber1 = 0;
		        int flagOfNumber2 = 0;
		        
				for(int z=0; z<5; z++){
					if(predictOf5[i][z]==maxIndex || predictOf5[i][z]==minIndex){
						
						if(predictOf5[i][z]==maxIndex && flagOfNumber1==0){
							flagOfNumber1++;
							continue;
						}else if(predictOf5[i][z]==minIndex && flagOfNumber2==0){
							flagOfNumber2++;
							continue;
						}
					}
					sumOfPredict5 += predictOf5[i][z];
				}
				System.out.println("总共：" + sumOfPredict5);
				int s3 = (int) Math.round((sumOfPredict5/3));
				System.out.println("加入最后结果的经过ceil预测值："+ s3);
//				String s1 = String.valueOf(s3);	
//				String s2 = s1.substring(0, s1.indexOf("."));
//				int s4 = Integer.parseInt(s2);
//				System.out.println("加入最后结果的预测值："+ s4);
				resultsAtLast.add(s3);
			}

		for (int i = 0; i < flavorsInfo.size(); i++) {
			System.out.println(flavorsInfo.get(i) + ":" + resultsAtLast.get(i));
		}

		return resultsAtLast;
*/
	}

	
	public static void weightSort(List<Integer> resultAtLast){
		double[] cpuWeight = new double[cpusInfo.size()];
		double[] memWeight = new double[memsInfo.size()];
		double[] endWeight = new double[resultAtLast.size()];
		
		if(cpuOrmem.equals("CPU")){
			flagOptimization = 0;
		}
		flagOptimization = 1;
		
		for(int i=0; i<cpusInfo.size(); i++){
			cpuWeight[i] = cpusInfo.get(i)*1.0/serverCpu;
			memWeight[i] = memsInfo.get(i)*1.0/serverMem;
			if(flagOptimization == 0){
				endWeight[i] = cpuWeight[i]/memWeight[i];
			}else{
				endWeight[i] = memWeight[i]/cpuWeight[i];
			}		
		}
		
		for(int i=0; i<endWeight.length; i++){
			for(int j=i+1; j<endWeight.length; j++){
				if(endWeight[i] < endWeight[j]){
					String flavorTemp;
					int cpuTemp, memTemp, resultTemp;

					flavorTemp = flavorsInfo.get(i);
					flavorsInfo.set(i, flavorsInfo.get(j));
					flavorsInfo.set(j, flavorTemp);

					cpuTemp = cpusInfo.get(i);
					cpusInfo.set(i, cpusInfo.get(j));
					cpusInfo.set(j, cpuTemp);

					memTemp = memsInfo.get(i);
					memsInfo.set(i, memsInfo.get(j));
					memsInfo.set(j, memTemp);
					
					resultTemp = resultAtLast.get(i);
					resultAtLast.set(i, resultAtLast.get(j));
					resultAtLast.set(j, resultTemp);
				}
			}
		}
		for (int i = 0; i < flavorsInfo.size(); i++) {
			System.out.println(flavorsInfo.get(i) + " " + cpusInfo.get(i) + " "
					+ memsInfo.get(i) + " " + resultAtLast.get(i)) ;
		}
		
	}
	
	/*
	 * 自适应放置算法，但是对虚拟机按维度进行了排序，效果并不是很好
	 */
/*	public static String[] allocateFlavor(List<Integer> resultsAtLast) {
		
		weightSort(resultsAtLast);//按照维度来排序
		
	//	List<Integer> resultsAtLatTemp = resultsAtLat;
		List<Integer> resultsAtLatCopy = new ArrayList<Integer>();
		List<Integer> resultsAtLatTemp = new ArrayList<Integer>();
		resultsAtLatTemp.addAll(resultsAtLast);
		resultsAtLatCopy.addAll(resultsAtLast);
		List<Deploy> deploys = new ArrayList<Deploy>();

		Deploy newDeploy = new Deploy();
		newDeploy.phyid = deploys.size() + 1;
		newDeploy.vflavors = flavorsInfo;
		newDeploy.nums = new int[flavorsInfo.size()];
		deploys.add(newDeploy);

		for (int i = flavorsInfo.size() - 1; i >= 0; i--) {
			Deploy newDeployTemp = deploys.get(deploys.size() - 1);
			while (resultsAtLatTemp.get(i) != 0) {
				// 还有cpu和mem可以分配
				if (cpusInfo.get(i) <= newDeployTemp.cpuLeft
						&& memsInfo.get(i) <= newDeployTemp.memLeft) {
					newDeployTemp.nums[i]++;
					newDeployTemp.cpuLeft -= cpusInfo.get(i);
					newDeployTemp.memLeft -= memsInfo.get(i);
					resultsAtLatTemp.set(i, resultsAtLatTemp.get(i) - 1);
				} else {
					int j = i - 1;
					for (j = i - 1; j >= 0; j--) {
						if (cpusInfo.get(j) <= newDeployTemp.cpuLeft
								&& memsInfo.get(j) <= newDeployTemp.memLeft) {
							newDeployTemp.nums[j]++;
							newDeployTemp.cpuLeft -= cpusInfo.get(j);
							newDeployTemp.memLeft -= memsInfo.get(j);
							resultsAtLatTemp.set(j, resultsAtLatTemp.get(j) - 1);
							break;
						}
					}
					if (j >= 0) {
						continue;
					} else {
						deploys.remove(deploys.size() - 1);
						deploys.add(newDeployTemp);
						// StringBuffer strbuf= new StringBuffer();
						// for(int q=0;
						// q<deploys.get(deploys.size()-1).vflavors.size();
						// q++){
						// if(deploys.get(deploys.size()-1).nums[q] != 0){
						// strbuf.append(deploys.get(deploys.size()-1).vflavors.get(q)
						// + " ");
						// strbuf.append(deploys.get(deploys.size()-1).nums[q] +
						// " ");
						// }
						// }
						// System.out.println(strbuf);

						Deploy newDeploy1 = new Deploy();
						newDeploy1.phyid = deploys.size() + 1;
						newDeploy1.vflavors = flavorsInfo;
						newDeploy1.nums = new int[flavorsInfo.size()];
						deploys.add(newDeploy1);
						break;
					}
				}
			}
			if (resultsAtLatTemp.get(i) == 0
					|| deploys.get(deploys.size() - 1).cpuLeft != serverCpu
					|| deploys.get(deploys.size() - 1).memLeft != serverMem) {
				deploys.remove(deploys.size() - 1);
				deploys.add(newDeployTemp);
			}
			if (resultsAtLatTemp.get(i) != 0) {
				i++;
			}
		}

		for (int i = 0; i < deploys.size(); i++) {
			StringBuffer strbuf1 = new StringBuffer();
			for (int q = 0; q < deploys.get(i).vflavors.size(); q++) {
				if (deploys.get(i).nums[q] != 0) {
					strbuf1.append(deploys.get(i).vflavors.get(q) + " ");
					strbuf1.append(deploys.get(i).nums[q] + " ");
				}
			}
			System.out.println(strbuf1);
		}

		int totalOfFlavor = 0;
		for (int i = 0; i < resultsAtLatCopy.size(); i++) {
			// System.out.println(resultsAtLatCopy.get(i));
			totalOfFlavor += resultsAtLatCopy.get(i);
		}

		String[] resultOfOutput = new String[flavorsInfo.size()
				+ deploys.size() + 3];
		resultOfOutput[0] = String.valueOf(totalOfFlavor);
		for (int i = 0; i < flavorsInfo.size(); i++) {
			resultOfOutput[i + 1] = flavorsInfo.get(i) + " "
					+ resultsAtLatCopy.get(i);
		}
		resultOfOutput[flavorsInfo.size() + 1] = "";
		resultOfOutput[flavorsInfo.size() + 2] = String.valueOf(deploys.size());
		for (int i = 0; i < deploys.size(); i++) {
			StringBuffer strbuf = new StringBuffer();
			strbuf.append(deploys.get(i).phyid + " ");
			for (int j = 0; j < deploys.get(i).vflavors.size(); j++) {
				if (deploys.get(i).nums[j] != 0) {
					strbuf.append(deploys.get(i).vflavors.get(j) + " ");
					strbuf.append(deploys.get(i).nums[j] + " ");
				}
			}

			resultOfOutput[flavorsInfo.size() + 3 + i] = new String(strbuf);
			// System.out.println(resultOfOutput[flavorsInfo.size()+3+i]);
		}

		return resultOfOutput;
	}
	
	/*
	 * 自适应放置算法
	 */
/*	public static String[] allocateFlavor1(List<Integer> resultsAtLat) {
	//	List<Integer> resultsAtLatTemp = resultsAtLat;
		List<Integer> resultsAtLatCopy = new ArrayList<Integer>();
		List<Integer> resultsAtLatTemp = new ArrayList<Integer>();
		resultsAtLatTemp.addAll(resultsAtLat);
		resultsAtLatCopy.addAll(resultsAtLat);
		List<Deploy> deploys = new ArrayList<Deploy>();

		Deploy newDeploy = new Deploy();
		newDeploy.phyid = deploys.size() + 1;
		newDeploy.vflavors = flavorsInfo;
		newDeploy.nums = new int[flavorsInfo.size()];
		deploys.add(newDeploy);

		for (int i = flavorsInfo.size() - 1; i >= 0; i--) {
			Deploy newDeployTemp = deploys.get(deploys.size() - 1);
			while (resultsAtLatTemp.get(i) != 0) {
				// 还有cpu和mem可以分配
				if (cpusInfo.get(i) <= newDeployTemp.cpuLeft
						&& memsInfo.get(i) <= newDeployTemp.memLeft) {
					newDeployTemp.nums[i]++;
					newDeployTemp.cpuLeft -= cpusInfo.get(i);
					newDeployTemp.memLeft -= memsInfo.get(i);
					resultsAtLatTemp.set(i, resultsAtLatTemp.get(i) - 1);
				} else {
					int j = i - 1;
					for (j = i - 1; j >= 0; j--) {
						if (cpusInfo.get(j) <= newDeployTemp.cpuLeft
								&& memsInfo.get(j) <= newDeployTemp.memLeft) {
							newDeployTemp.nums[j]++;
							newDeployTemp.cpuLeft -= cpusInfo.get(j);
							newDeployTemp.memLeft -= memsInfo.get(j);
							resultsAtLatTemp.set(j, resultsAtLatTemp.get(j) - 1);
							break;
						}
					}
					if (j >= 0) {
						continue;
					} else {
						deploys.remove(deploys.size() - 1);
						deploys.add(newDeployTemp);
						// StringBuffer strbuf= new StringBuffer();
						// for(int q=0;
						// q<deploys.get(deploys.size()-1).vflavors.size();
						// q++){
						// if(deploys.get(deploys.size()-1).nums[q] != 0){
						// strbuf.append(deploys.get(deploys.size()-1).vflavors.get(q)
						// + " ");
						// strbuf.append(deploys.get(deploys.size()-1).nums[q] +
						// " ");
						// }
						// }
						// System.out.println(strbuf);

						Deploy newDeploy1 = new Deploy();
						newDeploy1.phyid = deploys.size() + 1;
						newDeploy1.vflavors = flavorsInfo;
						newDeploy1.nums = new int[flavorsInfo.size()];
						deploys.add(newDeploy1);
						break;
					}
				}
			}
			if (resultsAtLatTemp.get(i) == 0
					|| deploys.get(deploys.size() - 1).cpuLeft != serverCpu
					|| deploys.get(deploys.size() - 1).memLeft != serverMem) {
				deploys.remove(deploys.size() - 1);
				deploys.add(newDeployTemp);
			}
			if (resultsAtLatTemp.get(i) != 0) {
				i++;
			}
		}

		for (int i = 0; i < deploys.size(); i++) {
			StringBuffer strbuf1 = new StringBuffer();
			for (int q = 0; q < deploys.get(i).vflavors.size(); q++) {
				if (deploys.get(i).nums[q] != 0) {
					strbuf1.append(deploys.get(i).vflavors.get(q) + " ");
					strbuf1.append(deploys.get(i).nums[q] + " ");
				}
			}
			System.out.println(strbuf1);
		}

		int totalOfFlavor = 0;
		for (int i = 0; i < resultsAtLatCopy.size(); i++) {
			// System.out.println(resultsAtLatCopy.get(i));
			totalOfFlavor += resultsAtLatCopy.get(i);
		}

		String[] resultOfOutput = new String[flavorsInfo.size()
				+ deploys.size() + 3];
		resultOfOutput[0] = String.valueOf(totalOfFlavor);
		for (int i = 0; i < flavorsInfo.size(); i++) {
			resultOfOutput[i + 1] = flavorsInfo.get(i) + " "
					+ resultsAtLatCopy.get(i);
		}
		resultOfOutput[flavorsInfo.size() + 1] = "";
		resultOfOutput[flavorsInfo.size() + 2] = String.valueOf(deploys.size());
		for (int i = 0; i < deploys.size(); i++) {
			StringBuffer strbuf = new StringBuffer();
			strbuf.append(deploys.get(i).phyid + " ");
			for (int j = 0; j < deploys.get(i).vflavors.size(); j++) {
				if (deploys.get(i).nums[j] != 0) {
					strbuf.append(deploys.get(i).vflavors.get(j) + " ");
					strbuf.append(deploys.get(i).nums[j] + " ");
				}
			}

			resultOfOutput[flavorsInfo.size() + 3 + i] = new String(strbuf);
			// System.out.println(resultOfOutput[flavorsInfo.size()+3+i]);
		}

		return resultOfOutput;
	}
*/	
	
	/*
	 * 模拟退火进行找最优解
	 */
	public static void allocateFlavor2(List<Integer> resultsAtLat, List<Deploy> serverBestDeploy) {
		
		weightSort(resultsAtLast);//按照维度来排序
		
		
		List<Integer> resultsAtLatTemp = new ArrayList<Integer>();
		resultsAtLatTemp.addAll(resultsAtLat);
		
		
		List<Flavor> predictedFlavors = new ArrayList<>();// 放着所有预测出来的虚拟机序列
		
		for(int i=0; i<resultsAtLatTemp.size(); i++){
			while(resultsAtLatTemp.get(i)>=1){
				predictedFlavors.add(new Flavor(flavorsInfo.get(i), memsInfo.get(i), cpusInfo.get(i)));
				resultsAtLatTemp.set(i, resultsAtLatTemp.get(i)-1);
			}
		}
		
		double minServerDeploy = predictedFlavors.size() + 1;
		List<Deploy> serverDeployEnd = new ArrayList<Deploy>();// 最终放置最好的放置结果
		
		double Temperature = 100.0;// 初始温度
		double Tmin = 1.0;// 终止温度
		double decline = 0.9999; // 温度下降系数
		List<Integer> dice = new ArrayList<>();// 骰子
		for(int i=0; i<predictedFlavors.size(); i++){
			dice.add(i);
		}
		
		Random rand = new Random(10);// 随机种子
		
		while(Temperature>Tmin){
			Collections.shuffle(dice);// 随机打乱骰子
			List<Flavor> newPredictFlavors = new ArrayList<>();
			newPredictFlavors.addAll(predictedFlavors);// 临时的虚拟机序列，这个地方进行深度copy会不会花费太大的内存
			Collections.swap(newPredictFlavors, dice.get(0), dice.get(1));
			
			List<Deploy> serverDeployTemp = new ArrayList<Deploy>();// 临时放置最好的放置结果
			Deploy deployOfFirst = new Deploy();// 第一台物理服务器
			serverDeployTemp.add(deployOfFirst);
			
			
			for(Flavor flavor : newPredictFlavors){
				int i=0;
				for(i=0; i<serverDeployTemp.size(); i++){
					if(serverDeployTemp.get(i).putFlavor(flavor)){
						break;
					}
				}
				if(i==serverDeployTemp.size()){
					Deploy deployOfNextServer = new Deploy();// 下一台物理服务器
					deployOfNextServer.putFlavor(flavor);
					serverDeployTemp.add(deployOfNextServer);
				}
			}
			
			double scoreOfEvaluate = 0;
			int indexOfServer = serverDeployTemp.size()-1;
			if(cpuOrmem.equals("CPU")){
	//			System.out.println(serverDeployTemp.get(indexOfServer).getCpuUsingRatio());
				scoreOfEvaluate = indexOfServer + serverDeployTemp.get(indexOfServer).getCpuUsingRatio();
			}else{
				scoreOfEvaluate = indexOfServer + serverDeployTemp.get(indexOfServer).getMemUsingRatio();
			}
			
			if(scoreOfEvaluate < minServerDeploy){
				minServerDeploy = scoreOfEvaluate;// 保留最小分数
				serverDeployEnd = serverDeployTemp;// 保存物理服务器
				predictedFlavors = newPredictFlavors;// 保存虚拟机序列
			}else{// 以一定概率保留分数高的结果
	//			System.out.println(Math.exp(minServerDeploy-scoreOfEvaluate)/Temperature);
	//			System.out.println(rand.nextInt(Integer.MAX_VALUE));
	//			System.out.println(rand.nextInt(Integer.MAX_VALUE)*1.0/Integer.MAX_VALUE);
				if((Math.exp(minServerDeploy-scoreOfEvaluate)/Temperature)>rand.nextInt(Integer.MAX_VALUE)*1.0/Integer.MAX_VALUE){
					minServerDeploy = scoreOfEvaluate;// 保留最小分数
					serverDeployEnd = serverDeployTemp;// 保存物理服务器
					predictedFlavors = newPredictFlavors;// 保存虚拟机序列
				}
			}
			 Temperature = Temperature * decline;
		}

		for (int i = 0; i < serverDeployEnd.size(); i++) {
			StringBuffer strbuf1 = new StringBuffer();
			for (int q = 0; q < serverDeployEnd.get(i).vflavors.size(); q++) {
					strbuf1.append(serverDeployEnd.get(i).vflavors.get(q).name + " ");
					strbuf1.append(serverDeployEnd.get(i).vflavors.size() + " ");
				
			}
			System.out.println(strbuf1);
		}
		
		serverBestDeploy.addAll(serverDeployEnd);// 保留一个指向部署服务器的指针
	}	
	/*
	 * 对最后一个物理服务器进行修改，并整理输出格式
	 */
	public static String[] settleOutput(List<Deploy> serverBestDeploy, List<Integer> resultsAtLast){
		
		int indexOfLastServer = serverBestDeploy.size()-1;// 得到最后一个物理服务器的下标
		
		StringBuffer strLastBuf = new StringBuffer();
		 Map<String, Integer> mapTemp = new HashMap<>();
		for (int j = 0; j < serverBestDeploy.get(indexOfLastServer).vflavors.size(); j++) {
			   
			    if(mapTemp.containsKey(serverBestDeploy.get(indexOfLastServer).vflavors.get(j).name)){
			    	mapTemp.put(serverBestDeploy.get(indexOfLastServer).vflavors.get(j).name, 
			    			mapTemp.get(serverBestDeploy.get(indexOfLastServer).vflavors.get(j).name) + 1);
			    }else
			    	mapTemp.put(serverBestDeploy.get(indexOfLastServer).vflavors.get(j).name, 1);
		}
		Set entrySetLast = mapTemp.entrySet();//key-value的set集合
		Iterator itLast = entrySetLast.iterator();
		
		int sumOfLastFlavorCpu = 0; //统计最后一个物理服务器的cpu所用总资源
		int sumOfLastFlavorMem = 0; //统计最后一个物理服务器的mem所用总资源
		double cpuLastUsingRatio = 0;// 部署当中最后一个物理服务器的cpu使用率
		double memLastUsingRatio = 0;// 部署当中最后一个物理服务器的mem使用率
		while(itLast.hasNext()){
			Map.Entry  me = (Map.Entry)itLast.next();
			int indexOfFlavorOnFlavorInfo = flavorsInfo.indexOf(me.getKey());
			sumOfLastFlavorCpu += cpusInfo.get(indexOfFlavorOnFlavorInfo)*(int)me.getValue();
			sumOfLastFlavorMem += memsInfo.get(indexOfFlavorOnFlavorInfo)*(int)me.getValue();
			strLastBuf.append(me.getKey() + " " + me.getValue() + " ");
		}
		System.out.println();
		System.out.println(strLastBuf);
		cpuLastUsingRatio = sumOfLastFlavorCpu/(serverCpu*1.0);// 最后一个物理服务器的cpu利用率
		memLastUsingRatio = sumOfLastFlavorCpu/(serverMem*1.0);// 最后一个物理服务器的mem利用率
		
		Set entrySetLastTemp = mapTemp.entrySet();//key-value的set集合
		Iterator itLastTemp = entrySetLast.iterator();
		if(cpuOrmem.equals("CPU")){
			System.exit(1);
			if(cpuLastUsingRatio < 0.2){// 如果最后一台物理服务器如果少于20%就删掉最后一台物理服务器
				while(itLastTemp.hasNext()){
					Map.Entry  me = (Map.Entry)itLastTemp.next();
					int indexOfFlavorOnFlavorInfo = flavorsInfo.indexOf(me.getKey());
					System.out.println(resultsAtLast.get(indexOfFlavorOnFlavorInfo));
					resultsAtLast.set(indexOfFlavorOnFlavorInfo,resultsAtLast.get(indexOfFlavorOnFlavorInfo)
							-(int)me.getValue());// 改变预测出来的虚拟机的个数
					System.out.println(resultsAtLast.get(indexOfFlavorOnFlavorInfo));
				}
				serverBestDeploy.remove(indexOfLastServer);// 则丢掉最后一个物理服务器的部署
			}else{// 否则最后一台物理服务器如果多于20%就填满最后一台物理服务器
				List<Flavor> predictedFlavors = new ArrayList<>();// 放着所有提供的虚拟机
				
				for(int i=0; i<flavorsInfo.size(); i++){
					predictedFlavors.add(new Flavor(flavorsInfo.get(i), memsInfo.get(i), cpusInfo.get(i)));
				}
				for(int i=0; i<predictedFlavors.size(); i++){// 对于提供的虚拟机遍历一遍，看看能不能把最后一台物理服务器填满
					Flavor flavor = predictedFlavors.get(i);
					if(serverBestDeploy.get(indexOfLastServer).putFlavor(flavor)){
						resultsAtLast.set(i, resultsAtLast.get(i)+1);
					}
				}
			}
			
		}else{
			if(memLastUsingRatio < 0.2){
				while(itLastTemp.hasNext()){
					Map.Entry  me = (Map.Entry)itLastTemp.next();
					int indexOfFlavorOnFlavorInfo = flavorsInfo.indexOf(me.getKey());
					
					resultsAtLast.set(indexOfFlavorOnFlavorInfo,resultsAtLast.get(indexOfFlavorOnFlavorInfo)
							-(int)me.getValue());// 改变预测出来的虚拟机的个数
					System.out.println(resultsAtLast.get(indexOfFlavorOnFlavorInfo));
				}
				serverBestDeploy.remove(indexOfLastServer);// 则丢掉最后一个物理服务器的部署
			}else{
				List<Flavor> predictedFlavors = new ArrayList<>();// 放着所有提供的虚拟机
				
				for(int i=0; i<flavorsInfo.size(); i++){
					predictedFlavors.add(new Flavor(flavorsInfo.get(i), memsInfo.get(i), cpusInfo.get(i)));
				}
				for(int i=0; i<predictedFlavors.size(); i++){ // 对于提供的虚拟机遍历一遍，看看能不能把最后一台物理服务器填满
					Flavor flavor = predictedFlavors.get(i);
					if(serverBestDeploy.get(indexOfLastServer).putFlavor(flavor)){
						resultsAtLast.set(i, resultsAtLast.get(i)+1);
					}
				}
			}
		}
	
		List<Integer> resultsAtLatCopy = new ArrayList<Integer>();
		resultsAtLatCopy.addAll(resultsAtLast);
		int totalOfFlavor = 0;
		for (int i = 0; i < resultsAtLatCopy.size(); i++) {
			// System.out.println(resultsAtLatCopy.get(i));
			totalOfFlavor += resultsAtLatCopy.get(i);
		}

		String[] resultOfOutput = new String[flavorsInfo.size()
				+ serverBestDeploy.size() + 3];
		resultOfOutput[0] = String.valueOf(totalOfFlavor);
		for (int i = 0; i < flavorsInfo.size(); i++) {
			resultOfOutput[i + 1] = flavorsInfo.get(i) + " "
					+ resultsAtLatCopy.get(i);
		}
		resultOfOutput[flavorsInfo.size() + 1] = "";
		resultOfOutput[flavorsInfo.size() + 2] = String.valueOf(serverBestDeploy.size());
		for (int i = 0; i < serverBestDeploy.size(); i++) {
			StringBuffer strbuf = new StringBuffer();
			strbuf.append(i+1 + " ");
			 Map<String, Integer> map = new HashMap<>();
			for (int j = 0; j < serverBestDeploy.get(i).vflavors.size(); j++) {
				   
				    if(map.containsKey(serverBestDeploy.get(i).vflavors.get(j).name)){
				    	map.put(serverBestDeploy.get(i).vflavors.get(j).name, 
				    			map.get(serverBestDeploy.get(i).vflavors.get(j).name) + 1);
				    }else
				    	map.put(serverBestDeploy.get(i).vflavors.get(j).name, 1);
			}
			Set entrySet = map.entrySet();//key-value的set集合
			Iterator it = entrySet.iterator();
			while(it.hasNext()){
				Map.Entry  me = (Map.Entry)it.next();
				strbuf.append(me.getKey() + " " + me.getValue() + " ");
			}

			resultOfOutput[flavorsInfo.size() + 3 + i] = new String(strbuf);
			System.out.println(resultOfOutput[flavorsInfo.size()+3+i]);
		}
		
		return resultOfOutput;
	}

	/*
	 * 设置输入数据
	 */
	public static void setInputInfo(String[] inputContent) {
		String line = inputContent[0];
		String[] phyServer = line.split(" ");
		serverCpu = Integer.parseInt(phyServer[0]);
		serverMem = Integer.parseInt(phyServer[1]) * 1024;// 转化为MB

		int numberOfFlavor = Integer.parseInt(inputContent[2]);
		String[] eachFlavor;
		for (int i = 3; i < 3 + numberOfFlavor; i++) {
			line = inputContent[i];
			eachFlavor = line.split(" ");

			flavorsInfo.add(eachFlavor[0]);
			cpusInfo.add(Integer.parseInt(eachFlavor[1]));
			memsInfo.add(Integer.parseInt(eachFlavor[2]));
		}

		cpuOrmem = inputContent[3 + numberOfFlavor + 1];
		predictBeginDate = inputContent[3 + numberOfFlavor + 3];
		predictEndDate = inputContent[3 + numberOfFlavor + 4];
		// daySpanOfPredict, beginTimeOfPredict, endTimeOfPredict
		beginTimeOfPredict = (int) getSecTime(predictBeginDate
				.replace(":", "-"));
		endTimeOfPredict = (int) getSecTime(predictEndDate.replace(":", "-"));
		daySpanOfPredict = (int) Math
				.ceil((double) (endTimeOfPredict - beginTimeOfPredict) / oneDay);
		System.out.println("要预测的时间间隔" + daySpanOfPredict);

		// 把虚拟机进行排序
		for (int i = 0; i < flavorsInfo.size(); i++) {
			for (int j = i + 1; j < flavorsInfo.size(); j++) {
				if (Integer.parseInt(flavorsInfo.get(i).substring(6)) > Integer
						.parseInt(flavorsInfo.get(j).substring(6))) {
					String flavorTemp;
					int cpuTemp, memTemp;

					flavorTemp = flavorsInfo.get(i);
					flavorsInfo.set(i, flavorsInfo.get(j));
					flavorsInfo.set(j, flavorTemp);

					cpuTemp = cpusInfo.get(i);
					cpusInfo.set(i, cpusInfo.get(j));
					cpusInfo.set(j, cpuTemp);

					memTemp = memsInfo.get(i);
					memsInfo.set(i, memsInfo.get(j));
					memsInfo.set(j, memTemp);
				}
			}
		}

		for (int i = 0; i < flavorsInfo.size(); i++) {
			System.out.println(flavorsInfo.get(i) + " " + cpusInfo.get(i) + " "
					+ memsInfo.get(i));
		}
	}

	/*
	 * 得到训练数据
	 */
	public static void setData(List<String> data) {
		int size = data.size();

		String begin_time = data.get(0).split(" ")[1];
		System.out.println("begin_time:" + begin_time);
		String end_time = data.get(size - 1).split(" ")[1];
		System.out.println("end_time:" + end_time);

		long beginTime1 = getSecTime(begin_time + " " + "00-00-00");
		long endTime2 = getSecTime(end_time + " " + "23-59-59");
		System.out.println(beginTime1);
		System.out.println(endTime2);

		daySpanOfinfo = (int) Math.ceil((double) (endTime2 - beginTime1)/oneDay);
		System.out.println(daySpanOfinfo);

		for (int i = 0; i < flavorsInfo.size(); i++) {
			int[] sequence = new int[daySpanOfinfo];
			String[] flavorAndTimeTemp;
			String flavorTemp;
			int dayDiff;
			for (int j = 0; j < data.size(); j++) {
				flavorAndTimeTemp = data.get(j).split(" ");
				flavorTemp = flavorAndTimeTemp[0];

				if (flavorTemp.equals(flavorsInfo.get(i))) {
					dayDiff = (int) ((getSecTime(flavorAndTimeTemp[1] + " " + flavorAndTimeTemp[2]) - beginTime1) / oneDay);
					sequence[dayDiff]++;
				}
			}

			sequences.add(sequence);
		}
		
		for (int i = 0; i < flavorsInfo.size(); i++) {
			System.out.print(flavorsInfo.get(i) + ":");
			int sum = 0;
			for (int j = 0; j < sequences.get(i).length; j++) {
				sum += sequences.get(i)[j];
				System.out.print(j + ":" + sequences.get(i)[j] + ", ");
			}
			System.out.println("total=" + sum);
		}
	}
	

	/*
	 * 把字符串型的时间转换成秒数
	 */
	public static long getSecTime(String time) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
		Date date = null;
		try {
			date = formatter.parse(time);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		long time1 = date.getTime() / 1000;
		return time1;
	}

}
