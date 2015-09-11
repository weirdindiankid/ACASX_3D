package acasx3d;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import sim.util.Double2D;
import sim.util.Double3D;
import acasx3d.generation.ACASX3DCState;
import acasx3d.generation.ACASX3DDTMC;
import acasx3d.generation.ACASX3DMDP;
import acasx3d.generation.ACASX3DUState;
import acasx3d.generation.ACASX3DUtils;
import acasx3d.generation.MDPValueIteration;

/**
 * @author Xueyi
 *
 */
public class ACASX3D 
{
	private LookupTable3D lookupTable3D;
	
	private int lastRA=0;//"COC"
	private Double3D ownshipLoc;
	private Double3D ownshipVel;
	private Double3D intruderLoc;
	private Double3D intruderVel;
	
	public ACASX3D()
	{
		lookupTable3D=LookupTable3D.getInstance();			
	}	
	
	public void update(Double3D ownshipLoc, Double3D ownshipVel, Double3D intruderLoc, Double3D intruderVel, int lastRA) 
	{
		this.ownshipLoc = ownshipLoc;	
		this.ownshipVel= ownshipLoc;
		this.intruderLoc = intruderLoc;	
		this.intruderVel= intruderLoc;
		this.lastRA = lastRA;
	}
	
	public int execute()
	{
		Double2D vctDistance = new Double2D(intruderLoc.x-ownshipLoc.x, intruderLoc.z-ownshipLoc.z);
		double r=vctDistance.length();
		double h=(intruderLoc.y-ownshipLoc.y);
		
		Map<Integer, Double> qValuesMap = new TreeMap<>();
		if(Math.abs(h)<=ACASX3DMDP.UPPER_H && r<=ACASX3DDTMC.UPPER_R)
		{
			Map<Integer, Double> entryTimeDistribution =calculateEntryTimeDistributionDTMC();						
			qValuesMap = calculateQValuesMap(entryTimeDistribution);			
		}
		else
		{
			return 0;				
		}		
		
		
		double maxQValue=Double.NEGATIVE_INFINITY;
		int bestActionCode=0;
		Set<Entry<Integer,Double>> entrySet = qValuesMap.entrySet();
		for (Entry<Integer,Double> entry : entrySet) 
		{
			double value=entry.getValue();
			if(value-maxQValue>=0.0001)
			{
				maxQValue=value;
				bestActionCode=entry.getKey();
			}
		}	
		lastRA=bestActionCode;	
		return lastRA;
	}

	private Map<Integer, Double> calculateEntryTimeDistributionDTMC()
	{
		return calculateEntryTimeDistributionDTMC(ownshipLoc, ownshipVel, intruderLoc, intruderVel);

	}
	
	public Map<Integer, Double> calculateEntryTimeDistributionDTMC(Double3D ownshipLoc, Double3D ownshipVel, Double3D intruderLoc, Double3D intruderVel)
	{
		Double2D vctDistance = new Double2D(intruderLoc.x-ownshipLoc.x, intruderLoc.z-ownshipLoc.z);
		Double2D vctVelocity = new Double2D(intruderVel.x-ownshipVel.x, intruderVel.z-ownshipVel.z);
		double r=vctDistance.length();
		double rv=vctVelocity.length();
		double alpha=vctVelocity.angle()-vctDistance.angle();
		if(alpha> Math.PI)
 	   	{
			alpha= -2*Math.PI +alpha; 
 	   	}
		if(alpha<-Math.PI)
 	   	{
			alpha=2*Math.PI+alpha; 
 	   	}
		double theta = Math.toDegrees(alpha);
		
		double rRes=ACASX3DDTMC.rRes;
		double rvRes=ACASX3DDTMC.rvRes;
		double thetaRes=ACASX3DDTMC.thetaRes;
		
		ArrayList<AbstractMap.SimpleEntry<Integer, Double>> entryTimeMapProbs = new ArrayList<AbstractMap.SimpleEntry<Integer, Double>>();
		Map<Integer, Double> entryTimeDistribution = new TreeMap<>();// must be a sorted map

		assert (r<=ACASX3DDTMC.UPPER_R);
		assert (rv<=ACASX3DDTMC.UPPER_RV);
		assert (alpha>=-180 && alpha<=180);
	
		int rIdxL = (int)Math.floor(r/rRes);
		int rvIdxL = (int)Math.floor(rv/rvRes);
		int thetaIdxL = (int)Math.floor(theta/thetaRes);
		for(int i=0;i<=1;i++)
		{
			int rIdx = (i==0? rIdxL : rIdxL+1);
			int rIdxP= rIdx< 0? 0: (rIdx>ACASX3DDTMC.nr? ACASX3DDTMC.nr : rIdx);			
			for(int j=0;j<=1;j++)
			{
				int rvIdx = (j==0? rvIdxL : rvIdxL+1);
				int rvIdxP= rvIdx<0? 0: (rvIdx>ACASX3DDTMC.nrv? ACASX3DDTMC.nrv : rvIdx);
				for(int k=0;k<=1;k++)
				{
					int thetaIdx = (k==0? thetaIdxL : thetaIdxL+1);
					int thetaIdxP= thetaIdx<-ACASX3DDTMC.ntheta? -ACASX3DDTMC.ntheta: (thetaIdx>ACASX3DDTMC.ntheta? ACASX3DDTMC.ntheta : thetaIdx);
					
					ACASX3DUState approxUState= new ACASX3DUState(rIdxP, rvIdxP, thetaIdxP);
					int approxUStateOrder = approxUState.getOrder();
					double probability= (1-Math.abs(rIdx-r/rRes))*(1-Math.abs(rvIdx-rv/rvRes))*(1-Math.abs(thetaIdx-theta/thetaRes));
					for(int t=0;t<=MDPValueIteration.T;t++)
					{
						entryTimeMapProbs.add(new SimpleEntry<Integer, Double>(t,probability*lookupTable3D.entryTimeDistributionArr.get((t*lookupTable3D.numUStates)+ approxUStateOrder)) );
					}
					
				}
			}
		}
		double entryTimeLessThanTProb=0;
		for(AbstractMap.SimpleEntry<Integer, Double> entryTime_prob :entryTimeMapProbs)
		{			
			if(entryTimeDistribution.containsKey(entryTime_prob.getKey()))
			{
				entryTimeDistribution.put(entryTime_prob.getKey(), entryTimeDistribution.get(entryTime_prob.getKey())+entryTime_prob.getValue());
			}
			else
			{
				entryTimeDistribution.put(entryTime_prob.getKey(), entryTime_prob.getValue());
			}
			entryTimeLessThanTProb+=entryTime_prob.getValue();
		}
		entryTimeDistribution.put(MDPValueIteration.T+1, 1-entryTimeLessThanTProb);
		return entryTimeDistribution;
	}
	
	private Map<Integer, Double> calculateQValuesMap(Map<Integer, Double> entryTimeDistribution)
	{
		double h=(intruderLoc.y-ownshipLoc.y);
		double oVy=ownshipVel.y;
		double iVy=intruderVel.y;
//		state.information=String.format( "(%.1f, %.1f, %.1f, %d)",h,oVy,iVy,ra);
		
		double hRes=ACASX3DMDP.hRes;
		double oVRes=ACASX3DMDP.oVRes;
		double iVRes=ACASX3DMDP.iVRes;
		
		assert (Math.abs(h)<=ACASX3DMDP.UPPER_H);
		assert (Math.abs(oVy)<=ACASX3DMDP.UPPER_VY);
		assert (Math.abs(iVy)<=ACASX3DMDP.UPPER_VY);
		assert (lastRA>=0);
		
		Map<Integer, Double> qValuesMap = new TreeMap<>();
		ArrayList<AbstractMap.SimpleEntry<Integer, Double>> actionMapValues = new ArrayList<AbstractMap.SimpleEntry<Integer, Double>>();

		int hIdxL = (int)Math.floor(h/hRes);
		int oVyIdxL = (int)Math.floor(oVy/oVRes);
		int iVyIdxL = (int)Math.floor(iVy/iVRes);
		for(int i=0;i<=1;i++)
		{
			int hIdx = (i==0? hIdxL : hIdxL+1);
			int hIdxP= hIdx< -ACASX3DMDP.nh? -ACASX3DMDP.nh: (hIdx>ACASX3DMDP.nh? ACASX3DMDP.nh : hIdx);			
			for(int j=0;j<=1;j++)
			{
				int oVyIdx = (j==0? oVyIdxL : oVyIdxL+1);
				int oVyIdxP= oVyIdx<-ACASX3DMDP.noVy? -ACASX3DMDP.noVy: (oVyIdx>ACASX3DMDP.noVy? ACASX3DMDP.noVy : oVyIdx);
				for(int k=0;k<=1;k++)
				{
					int iVyIdx = (k==0? iVyIdxL : iVyIdxL+1);
					int iVyIdxP= iVyIdx<-ACASX3DMDP.niVy? -ACASX3DMDP.niVy: (iVyIdx>ACASX3DMDP.niVy? ACASX3DMDP.niVy : iVyIdx);
					
					ACASX3DCState approxCState= new ACASX3DCState(hIdxP, oVyIdxP, iVyIdxP, lastRA);
					int approxCStateOrder = approxCState.getOrder();
					double probability= (1-Math.abs(hIdx-h/hRes))*(1-Math.abs(oVyIdx-oVy/oVRes))*(1-Math.abs(iVyIdx-iVy/iVRes));

					for(Entry<Integer, Double> entryTime_prob :entryTimeDistribution.entrySet())
					{
						int t=entryTime_prob.getKey();
						double entryTimeProb= entryTime_prob.getValue();					

						int index=0, numActions=0;
						try
						{
							index =lookupTable3D.indexArr.get((t*lookupTable3D.numCStates)+ approxCStateOrder);
							numActions = lookupTable3D.indexArr.get((t*lookupTable3D.numCStates)+approxCStateOrder+1)-index;	
						}
						catch(ArrayIndexOutOfBoundsException e)
						{
							System.out.println((t*lookupTable3D.numCStates)+"    "+ approxCState+"     "+approxCStateOrder);
							System.exit(-1);
						}
														
						for (int n=0;n<numActions;n++) 
						{
							double qValue= lookupTable3D.costArr.get(index+n);
							int actionCode= lookupTable3D.actionArr.get(index+n);							
							actionMapValues.add(new SimpleEntry<Integer, Double>(actionCode,probability*entryTimeProb*qValue) );
						}			
					}														
				}
			}
		}
					
		for(AbstractMap.SimpleEntry<Integer, Double> action_value :actionMapValues)
		{
			if(qValuesMap.containsKey(action_value.getKey()))
			{
				qValuesMap.put(action_value.getKey(), qValuesMap.get(action_value.getKey())+action_value.getValue());
			}
			else
			{
				qValuesMap.put(action_value.getKey(), action_value.getValue());
			}
		}
		
		return qValuesMap;
	}
	
	public double getActionV(int actionCode)
	{
		return ACASX3DUtils.getActionV(actionCode);
	
	}
	
	public double getActionA(int actionCode)
	{
		return ACASX3DUtils.getActionA(actionCode);
	
	}
	
}
