/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.SampledResult;
import org.vde.database.Database;
import org.vde.database.ResultObject;
import org.vde.database.SamplingBasedExecutor;


/**
 *
 * @author srahman7
 */
public class ApproxHeatMapSamp {

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static int totalInstances;
	// Map<String,RoaringBitmap> NeedleTail;
	Map<Integer, CellObject> groupAGG;
	
	int xMinF=-1,xMaxF=-1,yMinF=-1,yMaxF=-1;

	long returnTime;
	public double epsilon=1,delta=0.05,alpha=1;
		
	public Database db;
	public Map<String,Database> dbSets = new TreeMap<String,Database>();
	public HashMap<Integer, Short> withoutReplacementIndex = new HashMap<Integer, Short>();
	SamplingBasedExecutor sbe;

	TreeMap<Integer, ArrayList<Quadrant>> kBest;
	JSONObject jObjF;
	JSONArray jArrF;
	int[] groupsX;
	int[] groupsY;
	int[][] groupsXY;
	int[][] samplingcount;
	double[] groupsAVG;
	double avgMax=Double.MIN_VALUE,avgMin=Double.MAX_VALUE;
	String dir;
	String groupbyAttribute1;
	String groupbyAttribute2;
	String measureAttribute;
	String filterAttribute;
	String filterValue;
	String dataset;
	String[] measureValues;
	String samplingType;
	public boolean withoutReplacement = false;
	public boolean firstIteration = true;
	double[][] cArray = { { -53, 280 }, { -27, 135 }, { -7, 11 } };
	double c = 0;
	JSONObject cells;
	JSONArray cellArr;
	// BufferedWriter timedata;
	int sampleSize = 50000;
	public void setDbSets(Map<String,Database> dbSets)
	{
		
		this.dbSets = dbSets;
		
		System.out.println(this.dbSets.size());
		System.out.println(dbSets.size());
	
	}
	
	public String loadData(String info)
			throws IOException, InterruptedException, org.json.simple.parser.ParseException {
		System.out.println(info);
		JSONParser parser = new JSONParser();
		JSONObject jsonobj = (JSONObject) parser.parse(info);
		dataset = jsonobj.get("dataset").toString(); // "ds3_all_equal",
		
		System.out.println("-------------------------" + dataset + "-------------------------");

		// kBest = new TreeMap<Integer, ArrayList<Segment>>();
		jObjF = new JSONObject();
		jArrF = new JSONArray();
		cells = new JSONObject();
		cellArr = new JSONArray();
		dir = "./result/" + dataset + "/";

		String name = dataset;
		

		db = null;
		
		db = dbSets.get(dataset);

		System.out.println(db.databaseMetaData.getxAxisColumns().keySet());
		System.out.println(db.databaseMetaData.getyAxisColumns().keySet());

		JSONObject meta = new JSONObject();

		String xAxisStr = "";
		String yAxisStr = "";
		String predStr = "";
		int i = 0;
		for(String key:db.databaseMetaData.getxAxisColumns().keySet())
		{
			
			if(db.databaseMetaData.getHeatColumns().containsKey(key))
			{
				
				i++;
				xAxisStr+=key;
				if(i<db.databaseMetaData.getHeatColumns().keySet().size())
				{
					xAxisStr+=",";
				}
			}
			
		}
		
		i = 0;
		for (String key : db.databaseMetaData.getyAxisColumns().keySet()) {
			i++;
			yAxisStr += key;
			if (i < db.databaseMetaData.getyAxisColumns().keySet().size()) {
				yAxisStr += ",";
			}
		}
		i = 0;
		for (String key : db.databaseMetaData.getPredicateColumns().keySet()) {
			i++;
			predStr += key;
			if (i < db.databaseMetaData.getPredicateColumns().keySet().size()) {
				predStr += ",";
			}
		}
		meta.put("dimension1", xAxisStr);
		meta.put("dimension2", xAxisStr);
		meta.put("measure", yAxisStr);
		meta.put("predicate", predStr);
		return meta.toJSONString();

	}
	
	public String loadPredicateData(String info)
			throws IOException, InterruptedException, org.json.simple.parser.ParseException {
		JSONParser parser = new JSONParser();
		JSONObject jsonobj = (JSONObject) parser.parse(info);
		filterAttribute = jsonobj.get("predicate").toString();
		
		Map<String, RoaringBitmap> indexedbitmaps = db.getIndexedColumnValues(filterAttribute);
		
		JSONObject meta = new JSONObject();
		
		String predStr = "";
		
		int i = 0;
        for (String key : indexedbitmaps.keySet()) {
            i++;
            predStr += key;
            if (i < indexedbitmaps.keySet().size()) {
            	predStr += ",";
            }
        }
        
        if(filterAttribute.toLowerCase().contains("city"))
        {
        	predStr = "Atlanta- GA"+","+"Chicago- IL"+","+"Denver- CO"+","+"Houston- TX"+","+"Los Angeles- CA"+","+"New York- NY"+","+"Oklahoma- OH"; ;
        }
        else if(filterAttribute.toLowerCase().contains("state"))
        {
        	predStr = "AK,CA,FL,GA,IL,LA,MN,NY,OH,PA,TX";
        }
        else if(filterAttribute.toLowerCase().contains("airport"))
        {
        	predStr = "ATL,DEN,DFW,JFK,LAX,LGA,ORD,SFO,CTL,LAS,PHX";
        }
        else if(filterAttribute.toLowerCase().contains("carrier"))
        {
        	predStr = "AA,AS,CO,OH(1),UA,US";
        }
        
        meta.put("predicate", predStr);
		return meta.toJSONString();
		
	}

	public String loadAxesData(String info)
			throws IOException, InterruptedException, org.json.simple.parser.ParseException {
		System.out.println(info);
		JSONParser parser = new JSONParser();
		JSONObject jsonobj = (JSONObject) parser.parse(info);

		groupbyAttribute1 = jsonobj.get("dimension1").toString();
		groupbyAttribute2 = jsonobj.get("dimension2").toString();
		measureAttribute = jsonobj.get("measure").toString();
		filterAttribute = jsonobj.get("predicate").toString();
		filterValue = jsonobj.get("predicatevalue").toString();
		//samplingType = jsonobj.get("sampling").toString();
		sampleSize = Integer.parseInt(jsonobj.get("sampling").toString());
		//this.epsilon = Double.parseDouble(jsonobj.get("epsilon").toString());
		//this.alpha = Double.parseDouble(jsonobj.get("alpha").toString());
		kBest = new TreeMap<Integer, ArrayList<Quadrant>>();

		firstIteration = true;
		Map<String, RoaringBitmap> indexedbitmaps = db.getIndexedColumnValues(groupbyAttribute1);

		Map<String, RoaringBitmap> indexedbitmaps1 = db.getIndexedColumnValues(groupbyAttribute2);

		measureValues = db.getUnIndexedColumnValues(measureAttribute);

		// System.out.println(db.getIndexedColumnValues(groupbyAttribute).keySet());
		org.vde.database.ColumnMetadata cmd1 = db.getColumnMetaData(groupbyAttribute1);
		System.out.println("xMin:" + cmd1.min + "xMax:" + cmd1.max);

		org.vde.database.ColumnMetadata cmd2 = db.getColumnMetaData(groupbyAttribute2);
		System.out.println("yMin:" + cmd2.min + "yMax:" + cmd2.max);
		
		org.vde.database.ColumnMetadata cmd3 = db.getColumnMetaData(measureAttribute);
		
		c = cmd3.c_max - cmd3.c_min;
		
		epsilon = c/20;
		
		int xMin, xMax, yMin, yMax;

		xMin = (int) cmd1.min;
		xMax = (int) cmd1.max;

		yMin = (int) cmd2.min;
		yMax = (int) cmd2.max;

		Object[] hObj = indexedbitmaps.keySet().toArray();
		groupsX = new int[xMax - xMin + 1];// [hObj.length];

		hObj = indexedbitmaps1.keySet().toArray();
		groupsY = new int[yMax - yMin + 1];// [hObj.length];

		groupsXY = new int[groupsX.length][groupsY.length];

		groupAGG = new HashMap<Integer, CellObject>();

		samplingcount = new int[xMax - xMin + 1][yMax - yMin + 1];// [hObj.length];

		sbe = new SamplingBasedExecutor(db);

		for (int i = 0, j = xMin, cellIdx = 1; j <= xMax; i++, j++) {
			groupsX[i] = j;
			for (int k = 0, l = yMin; l <= yMax; k++, l++) {
				groupsY[k] = l;
				groupsXY[i][k] = cellIdx++;
				// System.out.println(i+"--"+groupsX[i]+","+k+"--"+groupsY[k]+",=="+groupsXY[i][k]);
				samplingcount[i][k] = 0;
				
				
				groupAGG.put((groupsXY[i][k]), new CellObject(groupsXY[i][k]));
			}

		}

		
		Arrays.sort(groupsX);
		Arrays.sort(groupsY);

		JSONObject meta = new JSONObject();
		
		meta.put("xMin", xMin);

		meta.put("xMax", xMax);

		meta.put("yMin", yMin);

		meta.put("yMax", yMax);

		return meta.toJSONString();

	}

	
	public String getData(String query) throws org.json.simple.parser.ParseException {

		System.out.println(query);
		String result = "";
		JSONParser parser = new JSONParser();
		

		try {
			

			for (int i = 1; i <= 800; i++) {

				// timedata.write(i + "," );
				long start = System.nanoTime();
				System.out.println("---------------Iteration ----------------" + i);

				result = genHeatMap(Long.toString(i), "0");
				JSONObject jsonobj = (JSONObject) parser.parse(result);
				JSONArray jArrSeg = (JSONArray) jsonobj.get("sl");
				if(jArrSeg.isEmpty())
					return result;
				
			}
			// timedata.close();
		} catch (IOException ex) {
			Logger.getLogger(ApproxHeatMapSamp.class.getName()).log(Level.SEVERE, null, ex);
		}

		
		return result;

	}

	public void reset() {
		kBest = new TreeMap<Integer, ArrayList<Quadrant>>();

		firstIteration = true;

		for (int i = 0; i < groupsX.length; i++) {
			for (int j = 0; j < groupsY.length; j++) {
				groupAGG.get(groupsXY[i][j]).avg = 0;
				samplingcount[i][j] = 0;
				
			}

		}

		sbe = new SamplingBasedExecutor(db);
		jObjF = new JSONObject();
		jArrF = new JSONArray();
		cells = new JSONObject();
		cellArr = new JSONArray();
		
		xMinF=-1;
		xMaxF=-1;
		yMinF=-1;
		yMaxF=-1;
	}

	/**
	 * @param k
	 * @param jsonSave
	 * @throws IOException
	 */
	public void genJSON(int k, String jsonSave) throws IOException {
		// NumberFormat formatter = new DecimalFormat("#0.00");
		JSONObject jObjTmp;
		JSONObject jObjSeg;
		JSONArray jArrSeg;
		JSONObject jObjEst;
		JSONArray jArrEst;
		
		BufferedWriter segmentJSON = null;
		
		jObjSeg = new JSONObject();
		jArrSeg = new JSONArray();
		jObjEst = new JSONObject();
		jArrEst = new JSONArray();
		double max=Integer.MIN_VALUE;
		double min=Integer.MAX_VALUE;
		for (int s = 0; s < kBest.get(k).size(); s++) {
			
			if(max<kBest.get(k).get(s).mean)
				max = kBest.get(k).get(s).mean;
			
			if(min>kBest.get(k).get(s).mean)
				min = kBest.get(k).get(s).mean;
			
			
			if (kBest.get(k).get(s).isNew) {
				jObjSeg.put("n", s);
				jObjTmp = new JSONObject();
				jObjTmp.put("xb", groupsX[kBest.get(k).get(s).xStart]);
				jObjTmp.put("xe", groupsX[kBest.get(k).get(s).xEnd]);
				jObjTmp.put("yb", groupsY[kBest.get(k).get(s).yStart]);
				jObjTmp.put("ye", groupsY[kBest.get(k).get(s).yEnd]);

				
				jObjTmp.put("a", kBest.get(k).get(s).mean);// Double.parseDouble(formatter.format(kBest.get(k).get(s).mean))
				// System.out.println(Double.parseDouble(formatter.format(kBest.get(k).get(s).mean)));
				jObjSeg.put("s", jObjTmp);

				jArrSeg.add(jObjSeg);

				jObjSeg = new JSONObject();

				
			}

			// segmentVal.write(groupsXY[kBest.get(k).get(s).start]+","+groups[kBest.get(k).get(s).end]+","+kBest.get(k).get(s).mean+"\n");
		}

		jObjF.put("sl", jArrSeg);
		if(firstIteration)
		{
			
			min=Math.abs(max)/max;
			jObjF.put("validCells", cellArr);
			
			jObjF.put("xMin", groupsX[xMinF]);
			jObjF.put("xMax", groupsX[xMaxF]);
			jObjF.put("yMin", groupsY[yMinF]);
			jObjF.put("yMax", groupsY[yMaxF]);
			
		}
			jObjF.put("max", max);
			jObjF.put("min", min);
		
		jObjF.put("gl", "");

		
		if (jsonSave.equals("1")) {
			segmentJSON = new BufferedWriter(new FileWriter(dir + "currentIt.json"));
			segmentJSON.write(JSONObject.toJSONString(jObjF));
			segmentJSON.close();
		}

		
	}

	public String genHeatMap(String str, String jsonSave) throws IOException {
		int k = Integer.parseInt(str);

	    epsilon = 8;
	    delta = 0.05;

		int xStart;
		int xEnd;
		int yStart;
		int yEnd;
		
		int xDim,yDim;

		// incrementally approx histogram
		int sampledInstances = 0;

		xStart = 0;
		xEnd = groupsX.length - 1;
		yStart = 0;
		yEnd = groupsY.length - 1;

		
		JSONObject jObj;
		JSONArray jArr;
		BufferedWriter segmentJSON = null;

		
		if (firstIteration) {
			// approx = new BufferedWriter(new
			// FileWriter(dir+"approx_"+k+".csv"));

			
			ArrayList<Quadrant> tempSeg = new ArrayList<Quadrant>();
			// sample from m groups and estimate mean
			double alpha_ = 0;
			SampledResult sampledResult;

			long samplingTime = 0;
			long aggregationTime = 0;
			long sampAggTime = System.nanoTime();
			ResultObject rObj = null;
			
			int [] yMinArray = new int[xEnd - xStart + 1];
			
			for (int i = xStart; i <= xEnd; i++) {
				yMinArray[i-xStart]=-1;
				for (int j = yStart; j <= yEnd; j++) {
					
						{
							/*sampledInstances = (int) Math
									.ceil((Math.log((4.0 * (groupsX.length * groupsY.length)) / delta) )
											* (100*c*c*groupsX.length * groupsY.length / (groupsX.length * groupsY.length*groupsX.length * groupsY.length*epsilon* epsilon)));*/ // it
						}

						sampledInstances = (int)((1.0*sampleSize)/(groupsX.length*groupsY.length)); 
						
						if (this.withoutReplacement) {
							
							long start = System.nanoTime();
							sampledResult = sbe.getANDSampledGroupWithoutReplacement(groupbyAttribute1,
									Integer.toString(groupsX[i]), groupbyAttribute2, Integer.toString(groupsY[j]),
									sampledInstances);
							

							samplingTime += (System.nanoTime() - start);
							// long start = System.nanoTime();
							start = System.nanoTime();
							// System.out.println("Start aggregation");
							rObj = sbe.doAggregateOnSampledBits(sampledResult, measureValues, "avg",sampledInstances);
							groupAGG.get(groupsXY[i][j]).avg += rObj.average;

							aggregationTime += (System.nanoTime() - start);
							
							withoutReplacementIndex.put(groupsXY[i][j], sampledResult.getLastIndex()); // save
							

						} else {

							
							long start = System.nanoTime();

							//System.out.println(groupsX[i]+" Start sampling "+groupsY[j]+" Intances: "+sampledInstances);
							
							if(sampledInstances > db.rowCount)
								sampledInstances = (int) db.rowCount;
							
							if(filterAttribute.equals("")) 
								sampledResult = sbe.getANDSampledGroup(groupbyAttribute1, Integer.toString(groupsX[i]),
										groupbyAttribute2, Integer.toString(groupsY[j]), sampledInstances);
							else
							{	//System.out.println("In filter");
								sampledResult = sbe.getFilterAndSampledGroup(groupbyAttribute1, Integer.toString(groupsX[i]),groupbyAttribute2, Integer.toString(groupsY[j]),filterAttribute,filterValue,sampledInstances);
							}
							

							samplingTime += (System.nanoTime() - start);
							
							start = System.nanoTime();
							rObj = sbe.doAggregateOnSampledBits(sampledResult, measureValues, "avg",sampledInstances);
							
							if(rObj.count > 0)
							{
								
								//System.out.println(groupsX[i]+"--"+groupsY[j]+"--"+rObj.count);
								
								if(xMinF==-1)
									xMinF = i;
								
								if(i>xMaxF)
									xMaxF= i;
								
								if(yMinArray[i-xStart]==-1)
									yMinArray[i-xStart] = j;
								
								if(j>yMaxF)
									yMaxF= j;
								
								groupAGG.get(groupsXY[i][j]).avg += rObj.average;
								
								//System.out.println(xMinF+","+xMaxF+","+yMinArray[i-xStart]+","+yMaxF);
								
																
							}
							else
							{
								
								samplingcount[i][j]=-1;
								//System.out.println(xMinF+","+i+","+yMinArray[i-xStart]+","+j);
								/*if(xMinF!=-1 && yMinArray[i-xStart]!=-1 && i >= xMinF && j >= yMinArray[i-xStart])
								{
									cells = new JSONObject();
									cells.put("x",groupsX[i]);
									cells.put("y",groupsY[j]);
									cellArr.add(cells);
									System.out.println(cells);
								}*/
							}
							
							aggregationTime += (System.nanoTime() - start);
						}

												
						alpha_ += groupAGG.get(groupsXY[i][j]).avg;
						samplingcount[i][j] +=sampledInstances;
					
				}
				

				
				
			}
			
			yMinF = Integer.MAX_VALUE;
			
			for(int y =0 ; y < yMinArray.length; y++)
			{
				if(yMinArray[y] < yMinF && yMinArray[y]!=-1)
					yMinF = yMinArray[y];
				
			}
			//System.out.println(xMinF+","+xMaxF+","+yMinF+","+yMaxF);
			for (int i = xMinF; i <= xMaxF; i++) {
				
				for (int j = yMinF; j <= yMaxF; j++) {
					if(samplingcount[i][j]==-1)
					{
						cells = new JSONObject();
						cells.put("x",groupsX[i]);
						cells.put("y",groupsY[j]);
						cellArr.add(cells);
						System.out.println(cells);
					}
				}
			}
			
			long histoTime = System.nanoTime();
			alpha_ = (1.0 / (groupsX.length * groupsY.length)) * alpha_;
			
			xStart = xMinF;
			xEnd = xMaxF;
			yStart = yMinF;
			yEnd = yMaxF;
			System.out.println(xStart+","+xEnd+","+yStart+","+yEnd);
			tempSeg.add(new Quadrant(xStart, xEnd, yStart, yEnd, alpha_));

			if (!jsonSave.equals("2")) {
				kBest.put(k, tempSeg);
				
				genJSON(k, jsonSave);
				// System.out.println(JSONObject.toJSONString(jObjF));
				firstIteration = false;
				return JSONObject.toJSONString(jObjF);
			}
			firstIteration = false;
			return "";

		} else {

			epsilon /= alpha;
			int countSingle = 0;
			ArrayList<Quadrant> tempQuad = new ArrayList<Quadrant>();
			ArrayList<Quadrant> prevQuad = new ArrayList<Quadrant>();
			Quadrant quadTemp;
			prevQuad = kBest.get(k - 1);

			double global_quad_mean = 0;
			int split_quad_no = -1;
			int split_quad_maxSplitX = -1;
			int split_quad_maxSplitY = -1;
			double global_max = -1000000000;
			double global_best_mean1 = 0, global_best_mean2 = 0, global_best_mean3 = 0, global_best_mean4 = 0;

			double size = 0;
			for (int s = 0; s < prevQuad.size(); s++) {
				size += (prevQuad.get(s).xEnd - prevQuad.get(s).xStart+1)* (prevQuad.get(s).xEnd - prevQuad.get(s).xStart+1)*
						 (prevQuad.get(s).yEnd - prevQuad.get(s).yStart+1)*(prevQuad.get(s).yEnd - prevQuad.get(s).yStart+1); 
			}
			
			for (int s = 0; s < prevQuad.size(); s++) {

				quadTemp = prevQuad.get(s);
				xStart = quadTemp.xStart;
				xEnd = quadTemp.xEnd;

				yStart = quadTemp.yStart;
				yEnd = quadTemp.yEnd;

				xDim = xEnd-xStart+1;
				yDim = yEnd-yStart+1;
				
				
				{

					// System.out.println("hereNew");
					double segment_tmp_avg = 0;

					double size1 = (prevQuad.get(s).xEnd - prevQuad.get(s).xStart+1)*(prevQuad.get(s).yEnd - prevQuad.get(s).yStart+1);
					
					sampledInstances = (int)(sampleSize*size1/size); 
					
					SampledResult sampledResult;
					ResultObject rObj = null;

					for (int i = xStart; i <= xEnd; i++) {
						for (int j = yStart; j <= yEnd; j++) {
							if (samplingcount[i][j] != -1) {
								
								{
									
								/*	sampledInstances = (int) Math
											.ceil((Math.log((4.0 * (groupsX.length * groupsY.length)) / delta) )
													* (100*c*c*xDim * yDim / (groupsX.length * groupsY.length*groupsX.length * groupsY.length*epsilon* epsilon))); // it
									*/
								}

								if (this.withoutReplacement) {
									long start = System.nanoTime();
									sampledResult = sbe.getANDSampledGroupWithoutReplacement(groupbyAttribute1,
											Integer.toString(groupsX[i]), groupbyAttribute2,
											Integer.toString(groupsY[j]), sampledInstances,
											withoutReplacementIndex.get(groupsXY[i][j]));
											
									start = System.nanoTime();
									// System.out.println("Start aggregation");
									rObj = sbe.doAggregateOnSampledBits(sampledResult, measureValues, "avg",sampledInstances);
									groupAGG.get(groupsXY[i][j]).avg = samplingcount[i][j]
											* groupAGG.get(groupsXY[i][j]).avg + rObj.count * rObj.average;
									groupAGG.get(groupsXY[i][j]).avg = groupAGG.get(groupsXY[i][j]).avg
											/ (samplingcount[i][j] + rObj.count);
									withoutReplacementIndex.put(groupsXY[i][j], sampledResult.getLastIndex()); // save
									
								} else {

									
									long start = System.nanoTime();

									if(sampledInstances > db.rowCount)
										sampledInstances = (int) db.rowCount;

									if(filterAttribute.equals("")) 
										sampledResult = sbe.getANDSampledGroup(groupbyAttribute1, Integer.toString(groupsX[i]),
												groupbyAttribute2, Integer.toString(groupsY[j]), sampledInstances);
									else
										sampledResult = sbe.getFilterAndSampledGroup(groupbyAttribute1, Integer.toString(groupsX[i]),groupbyAttribute2, Integer.toString(groupsY[j]),filterAttribute,filterValue,sampledInstances);
									

									start = System.nanoTime();
									rObj = sbe.doAggregateOnSampledBits(sampledResult, measureValues, "avg",sampledInstances);
									groupAGG.get(groupsXY[i][j]).avg = samplingcount[i][j]
											* groupAGG.get(groupsXY[i][j]).avg + rObj.count * rObj.average;// *******count
									if (samplingcount[i][j] + rObj.count <= 0) {
										groupAGG.get(groupsXY[i][j]).avg = 0;
									} else {
										groupAGG.get(groupsXY[i][j]).avg = groupAGG.get(groupsXY[i][j]).avg
												/ (samplingcount[i][j] + rObj.count);
									}

									
								}

								segment_tmp_avg += groupAGG.get(groupsXY[i][j]).avg;
								if (rObj.count > 0)
									samplingcount[i][j] +=sampledInstances;
								//bw.write(groupsX[i]+","+groupsY[j]+","+groupsXY[i][j]+","+rObj.count+","+rObj.average+","+groupAGG.get(groupsXY[i][j]).avg+","+samplingcount[i][j]+"\n");
							}
							
						}

					}

				}
			}
			
		
			long histoTime = System.nanoTime();
			for (int s = 0; s < prevQuad.size(); s++) {

				quadTemp = prevQuad.get(s);
				xStart = quadTemp.xStart;
				xEnd = quadTemp.xEnd;

				yStart = quadTemp.yStart;
				yEnd = quadTemp.yEnd;

				
				if ((xEnd - xStart) < 1 && (yEnd - yStart) < 1) {
					countSingle++;

					continue;
				}

				// calculate segment means for different choice of split and
				// find best split
				double alpha = 0;
				double quad_mean = 0;
				double best_mean1 = 0;
				double best_mean2 = 0;
				double best_mean3 = 0;
				double best_mean4 = 0;
				double tmp1 = 0, tmp2 = 0, tmp3 = 0, tmp4 = 0;
				double max = -1000000000;
				int maxSplitX = -1;
				int maxSplitY = -1;

				// estimate segment means means. calc alpha(i,j) for i=2 to m-1

				int toI, toJ;
				for (int i = xStart; i <= xEnd; i++) {
					for (int j = yStart; j <= yEnd; j++) {
											
						// split in 4
						alpha = 0;

						for (int kx = xStart; kx <= i; kx++) {
							for (int ky = j; ky <= yEnd; ky++) {
								alpha += groupAGG.get(groupsXY[kx][ky]).avg;
							}
						}

						alpha = (1.0 / ((i - xStart + 1) * (yEnd - j + 1))) * alpha; // V_s

						tmp1 = alpha;

						quad_mean += alpha * alpha * (i - xStart + 1) * (yEnd - j + 1);

						alpha = 0;

						for (int kx = i + 1; kx <= xEnd; kx++) {
							for (int ky = j; ky <= yEnd; ky++) {
								alpha += groupAGG.get(groupsXY[kx][ky]).avg;
							}
						}

						if(i<xEnd)
							alpha = (1.0 / ((xEnd - (i + 1) + 1) * (yEnd - j + 1))) * alpha; // V_s
						else
							alpha = 0;

						tmp2 = alpha;

						quad_mean += alpha * alpha * (xEnd - (i + 1) + 1) * (yEnd - j + 1);

						alpha = 0;

						
						for (int kx = i + 1; kx <= xEnd; kx++) {
							for (int ky = j - 1; ky >= yStart; ky--) {
								alpha += groupAGG.get(groupsXY[kx][ky]).avg;
							}
						}

						
						if(i<xEnd && j>yStart) 
							alpha = (1.0 / ((xEnd - (i + 1) + 1) * (j-1 - yStart + 1))) * alpha; // V_s
						else
							alpha = 0;
						
						tmp3 = alpha;

						quad_mean += alpha * alpha * (xEnd - (i + 1) + 1) * (j-1 - yStart + 1);
						
						alpha = 0;

						for (int kx = xStart; kx <= i; kx++) {
							for (int ky = j - 1; ky >= yStart; ky--) {
								alpha += groupAGG.get(groupsXY[kx][ky]).avg;
							}
						}

						if(j>yStart)
							alpha = (1.0 / ((i - xStart + 1) * (j-1 - yStart + 1))) * alpha; // V_s
						else
							alpha = 0;

						
						tmp4 = alpha;

						quad_mean += alpha * alpha * (i - xStart + 1) * (j-1 - yStart + 1);
						
						
						if (quad_mean > max) {
							max = quad_mean;
							maxSplitX = i;
							maxSplitY = j;

							best_mean1 = tmp1;
							best_mean2 = tmp2;
							best_mean3 = tmp3;
							best_mean4 = tmp4;
							
						}

						quad_mean = 0;
						alpha = 0;
					}

				}

				for (int g = 0; g < prevQuad.size(); g++) {
					if (g != s) {
						
					} else {

						/******** add to global mean the current max ********/

						global_quad_mean = max;

						/****************************/

					}

				}

				//bw.write("Global max,X,Y,mean1,mean2,mean3,mean4\n");
				if (global_quad_mean > global_max) {
					global_max = global_quad_mean;

					split_quad_no = s;
					split_quad_maxSplitX = maxSplitX;
					split_quad_maxSplitY = maxSplitY;

					global_best_mean1 = best_mean1;
					global_best_mean2 = best_mean2;
					global_best_mean3 = best_mean3;
					global_best_mean4 = best_mean4;
					
				}
				global_quad_mean = 0;

				//bw.write("=================================\n");

			}

			System.out.println(split_quad_no + "::" + split_quad_maxSplitX + ">>>>>>>>>>>>>>>>>" + split_quad_maxSplitY);
			// find best histogram. can be improved by bunary search to log(n)

			for (int s = 0; s < prevQuad.size(); s++) {
				if (s == split_quad_no) {

					if (split_quad_maxSplitX < prevQuad.get(s).xEnd) {
						if (split_quad_maxSplitY == prevQuad.get(s).yStart) {
							// split in two quad 1 and 2
							
							tempQuad.add(new Quadrant(prevQuad.get(s).xStart, split_quad_maxSplitX,
									prevQuad.get(s).yStart, prevQuad.get(s).yEnd, global_best_mean1));
							tempQuad.add(new Quadrant(split_quad_maxSplitX+1, prevQuad.get(s).xEnd,
									prevQuad.get(s).yStart, prevQuad.get(s).yEnd, global_best_mean2));
							

						} else {
							// split in four
							tempQuad.add(new Quadrant(prevQuad.get(s).xStart, split_quad_maxSplitX,
									split_quad_maxSplitY, prevQuad.get(s).yEnd, global_best_mean1));
							tempQuad.add(new Quadrant(split_quad_maxSplitX+1, prevQuad.get(s).xEnd,
									split_quad_maxSplitY, prevQuad.get(s).yEnd, global_best_mean2));
							tempQuad.add(new Quadrant(split_quad_maxSplitX+1, prevQuad.get(s).xEnd,
									prevQuad.get(s).yStart, split_quad_maxSplitY-1, global_best_mean3));
							tempQuad.add(new Quadrant(prevQuad.get(s).xStart, split_quad_maxSplitX,
									prevQuad.get(s).yStart, split_quad_maxSplitY-1, global_best_mean4));
							
						}
					} else {
						if (split_quad_maxSplitY == prevQuad.get(s).yStart) {
							// no split
							/*prevQuad.get(s).isNew = false;
							tempQuad.add(prevQuad.get(s));*/
							
							//lets split
							if(prevQuad.get(s).xEnd-prevQuad.get(s).xStart==0) 
							{
								//split in two
								if(prevQuad.get(s).yEnd-prevQuad.get(s).yStart!=0) // one column
								{
									double avgTemp = groupAGG.get(groupsXY[prevQuad.get(s).xEnd][prevQuad.get(s).yStart]).avg/1.0;
									
									double totalCells = (prevQuad.get(s).xEnd-prevQuad.get(s).xStart+1)* (prevQuad.get(s).yEnd-prevQuad.get(s).yStart+1);
									
									double newAvg = totalCells*(prevQuad.get(s).mean-avgTemp/totalCells)/(totalCells-1);
									
									tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd,
											prevQuad.get(s).yStart+1, prevQuad.get(s).yEnd, newAvg));
									tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd,
											prevQuad.get(s).yStart, prevQuad.get(s).yStart, avgTemp));
									
									
								}
								else //one cell
								{
									prevQuad.get(s).isNew = false;
									tempQuad.add(prevQuad.get(s));
									System.out.println("one cell: "+prevQuad.get(s).mean);
								}
								
							}
							else
							{
								//split in two
								if(prevQuad.get(s).yEnd-prevQuad.get(s).yStart==0) //one row
								{
									
									double avgTemp = groupAGG.get(groupsXY[prevQuad.get(s).xEnd][prevQuad.get(s).yStart]).avg/1.0;
									
									double totalCells = (prevQuad.get(s).xEnd-prevQuad.get(s).xStart+1)* (prevQuad.get(s).yEnd-prevQuad.get(s).yStart+1);
									
									double newAvg = totalCells*(prevQuad.get(s).mean-avgTemp/totalCells)/(totalCells-1);
									
									tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd-1,
											prevQuad.get(s).yStart, prevQuad.get(s).yEnd, newAvg));
									tempQuad.add(new Quadrant(prevQuad.get(s).xEnd, prevQuad.get(s).xEnd,
											prevQuad.get(s).yStart, prevQuad.get(s).yEnd, avgTemp));
									
									
								}
								else //total cell--rectangle. split in 4
								{
									double quad3 = groupAGG.get(groupsXY[prevQuad.get(s).xEnd][prevQuad.get(s).yStart]).avg/1.0; //quad 3
																		
									double quad1=0,quad2=0,quad4=0;
									//System.out.println("Quad 3: "+quad3);
									//quad 2
									//System.out.println("Quad 2");
									for(int q=prevQuad.get(s).yStart+1;q<=prevQuad.get(s).yEnd;q++)
									{
										quad2+=groupAGG.get(groupsXY[prevQuad.get(s).xEnd][q]).avg;
										//System.out.println(groupAGG.get(groupsXY[prevQuad.get(s).xEnd][q]).avg);
									}
									quad2 = quad2/(prevQuad.get(s).yEnd-(prevQuad.get(s).yStart+1)+1);
									//quad 4
									//System.out.println("Quad 4");
									for(int p=prevQuad.get(s).xStart;p<prevQuad.get(s).xEnd;p++)
									{
										quad4+=groupAGG.get(groupsXY[p][prevQuad.get(s).yStart]).avg;
										//System.out.println(groupAGG.get(groupsXY[p][prevQuad.get(s).yStart]).avg);
									}
									quad4 = quad4/(prevQuad.get(s).xEnd-1-prevQuad.get(s).xStart+1); 
									
									//quad 1
									/*System.out.println("Quad 1");
									for(int p=prevQuad.get(s).xStart;p<prevQuad.get(s).xEnd;p++)
									{
										for(int q=prevQuad.get(s).yStart+1;q<=prevQuad.get(s).yEnd;q++)
										{
											System.out.println(groupAGG.get(groupsXY[prevQuad.get(s).xEnd][q]).avg);
										}
									}*/
									
									quad1= prevQuad.get(s).mean-(quad2+quad3+quad4);
									
									tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd-1,
											prevQuad.get(s).yStart+1, prevQuad.get(s).yEnd, quad1));
									tempQuad.add(new Quadrant(prevQuad.get(s).xEnd, prevQuad.get(s).xEnd,
											prevQuad.get(s).yStart+1, prevQuad.get(s).yEnd, quad2));
									tempQuad.add(new Quadrant(prevQuad.get(s).xEnd, prevQuad.get(s).xEnd,
											prevQuad.get(s).yStart, prevQuad.get(s).yStart, quad3));
									tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd-1,
											prevQuad.get(s).yStart, prevQuad.get(s).yStart, quad4));
									
									//System.out.println("rectangle: "+quad1+","+quad2+","+quad3+","+quad4);
								}
								
							}
						} else {
							// split in two quad 1 and quad 4
							tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd,
									split_quad_maxSplitY,prevQuad.get(s).yEnd, global_best_mean1));
							tempQuad.add(new Quadrant(prevQuad.get(s).xStart, prevQuad.get(s).xEnd,
									prevQuad.get(s).yStart, split_quad_maxSplitY-1, global_best_mean4));
						}

					}

					
				} else {
					// System.out.println("In else");
					prevQuad.get(s).isNew = false;
					tempQuad.add(prevQuad.get(s));
				}
			}

			kBest.put(k, tempQuad);

			// timedata.write((System.nanoTime()-histoTime)/ 1000000+",");
			returnTime = System.nanoTime();
			

			genJSON(k, jsonSave);
			// System.out.println(JSONObject.toJSONString(jObjF));
			return JSONObject.toJSONString(jObjF);
		}

		// return "";
	}

}