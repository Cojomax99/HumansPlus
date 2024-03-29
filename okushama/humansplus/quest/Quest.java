package okushama.humansplus.quest;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import okushama.humansplus.Human;

import net.minecraft.client.Minecraft;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.InventoryBasic;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.PathEntity;
import net.minecraft.src.Potion;
import net.minecraft.src.PotionEffect;
import net.minecraft.src.World;

public class Quest {
	
	public File questConfig, questScript;
	public String nextQuest = null;
	ScriptEngineManager factory = new ScriptEngineManager();
    ScriptEngine engine = factory.getEngineByName("JavaScript");
    public MonitorCollection collection = new MonitorCollection();
    public MonitorScout scout = new MonitorScout();
	
	public String name, pretext, durtext, reqtext, fintext, flags, reward, image, npcname, npctype;
	
	public Human vendor;
	
	public int type; 
	
	public boolean hasStarted = false, hasFinished = false, requirementsMet = false;
	
	public String getText(){
		return !hasStarted ? pretext : (requirementsMet && !hasFinished) ? reqtext : (hasFinished ? fintext : durtext);
	}
	
	public String getPhase(){
		return !hasStarted ? "Vending" : (requirementsMet && !hasFinished) ? "Waiting for Finish" : (hasFinished ? "Complete" : "In Progress");
	}
	
	public EntityPlayer thePlayer = Minecraft.getMinecraft().thePlayer;
	
	public ArrayList<String> configLines = new ArrayList<String>();
	
	public Quest(String file){
		questConfig = new File(Minecraft.getMinecraftDir(), file);
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(questConfig)));
			String l = "";
			while((l = in.readLine()) != null){
				// We don't want #comments!
				if(!l.trim().startsWith("#"))
				configLines.add(l);
			}
			in.close();
		} catch (Exception e) {
			System.out.println("Attempted to load invalid Quest File! ("+file+")");
			e.printStackTrace();
			return;
		}
		setVariables();
	}
	
	public String swapForColourCodes(String s){
		String result = s;
		if(result.contains("\u0026")){
			result = result.replaceAll("\u0026", "\u00A7");
		}
		return result;
	}
	
	public void setVariables(){
		for(String line : configLines){
			try{
				String[] sline = line.split("=");
				String key = sline[0].toLowerCase(), value = sline[1];
				if(key.equals("name"))
					this.name = swapForColourCodes(value);
				if(key.equals("pretext"))
					this.pretext = swapForColourCodes(value);
				if(key.equals("reqtext"))
					this.reqtext = swapForColourCodes(value);
				if(key.equals("durtext"))
					this.durtext = swapForColourCodes(value);
				if(key.equals("fintext"))
					this.fintext = swapForColourCodes(value);
				if(key.equals("type")){
					if(value.toUpperCase().equals("COLLECTION"))
						this.type = Quest.COLLECTION;
					if(value.toUpperCase().equals("BOUNTY"))
						this.type = Quest.BOUNTY;
					if(value.toUpperCase().equals("SCOUT"))
						this.type = Quest.SCOUT;
					if(value.toUpperCase().equals("HUNTED"))
						this.type = Quest.HUNTED;
					
				}
				if(key.equals("flags"))
					this.flags = value;
				if(key.equals("reward"))
					this.reward = value;
				if(key.equals("image"))
					this.image = value;
				if(key.equals("npcname"))
					this.npcname = value;
				if(key.equals("npctype"))
					this.npctype = value;
				if(key.equals("script"))
					this.questScript = new File(Minecraft.getMinecraftDir().getAbsolutePath()+"/okushama/humansplus/quest/script/", value);
			}catch(Exception e){
				System.out.println("Quest invalid! "+questConfig.getName());
			}
		}
		//System.out.println(questScript.getAbsolutePath());
		
	}
	
	public void evalScript(){
        try {
        	ScriptHelper helper = new ScriptHelper(this);
        	engine.put("helper", helper);
        	engine.put("vendor", vendor);
        	engine.put("quest",this);
        	engine.put("out", System.out);
        	engine.put("mc", Minecraft.getMinecraft());
			engine.eval(new FileReader(questScript));
		} catch (Exception e) {
			System.out.println("Quest Script could not evaluate! ("+questScript.getAbsolutePath()+")");
		}
	}
	
	public void quitQuest(){
		try {
			engine.eval("quest_quit()");
		} catch (ScriptException e) {
		//	e.printStackTrace();
		}
		if(vendor!=null)
		vendor.setVendingQuest(false);
		HandlerQuests.currentQuest = null;
		
	}
	
	public void onUpdateDuringQuest(){
		if(type == Quest.SCOUT){
			scout.onUpdate();
		}
	}
	
	
	public void initQuest(){
		thePlayer = Minecraft.getMinecraft().thePlayer;
		if(this.assignQuestToHuman(100D)){
			//System.out.println("Init Quest: "+this.name);
			if(questScript != null)
				if(questScript.exists())
					evalScript();
			try {
				engine.eval("quest_init()");
			} catch (ScriptException e) {
			}
			((PanelQuest)HandlerQuests.panels.get(0)).setQuest(this);
			if(Minecraft.getMinecraft().getIntegratedServer() != null){
				List<EntityPlayer> l = Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList;
				for(int k = 0; k < l.size(); k++){
					if(l.get(k).username.equals(thePlayer.username)){
						this.thePlayer = l.get(k);
					}
				}
			}
		}else{
			//System.out.println("Could not find a suitable vendor!");
			this.quitQuest();
		}
	}
	
	public void startQuest(){
		this.hasStarted = true;
		if(type == COLLECTION){
			collection.setVarsToCurrentQuest();
		}
		if(type == SCOUT){
			scout.setVarsToCurrentQuest();
		}
		try {
			engine.eval("quest_start()");
		} catch (ScriptException e) {
		}
	}
	
	public void meetRequirements(){
		this.requirementsMet = true;
	//	System.out.println("Quest Requirements Met!");
		try {
			engine.eval("requirements_met()");
		} catch (ScriptException e) {
		}
	}
	
	public void completeQuest(){
		this.hasFinished = true;
		try {
			engine.eval("quest_complete()");
		} catch (ScriptException e) {
		}
		
		try{
			String flags = reward;
			if(!flags.contains(";")){
				flags = flags+";";
			}
			String potentialNextQuest = "";
			String[] flagss = flags.split(";");
			for(int i = 0; i < flagss.length; i++){
				String[] iFlag = flagss[i].split("\\:");
				
				if(iFlag[0].toLowerCase().equals("item")){
					String[] fl = iFlag[1].split("\\*");
					try{
						List<EntityPlayer> l = Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList;
						for(int k = 0; k < l.size(); k++){
							if(l.get(k).username.equals(thePlayer.username)){
								ItemStack is = new ItemStack(Integer.valueOf(fl[0]), Integer.valueOf(fl[1]), 0);
								l.get(k).inventory.addItemStackToInventory(is);
							}
						}
					}catch(Exception e){
						System.out.println("Could not parse Item!");
						e.printStackTrace();
					}
				}
				
				if(iFlag[0].toLowerCase().equals("exp")){
					try{
						List<EntityPlayer> l = Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList;
						for(int k = 0; k < l.size(); k++){
							if(l.get(k).username.equals(thePlayer.username)){
								int toAdd = 0;
								if(iFlag[1].endsWith("L")){
									toAdd = Integer.parseInt(iFlag[1].substring(0, iFlag[1].length()-1))*17;
								}else{
									l.get(k).experience += Integer.parseInt(iFlag[1]);
								}
								l.get(k).addExperience(toAdd);
							}
						}
					}catch(Exception e){
						System.out.println("Could not parse Experience!");
						e.printStackTrace();
					}
				}
				if(iFlag[0].toLowerCase().equals("buff")){
					try{
						String[] fl = iFlag[1].split("\\*");
						List<EntityPlayer> l = Minecraft.getMinecraft().getIntegratedServer().getConfigurationManager().playerEntityList;
						for(int k = 0; k < l.size(); k++){
							if(l.get(k).username.equals(thePlayer.username)){
								for(Potion p : Potion.potionTypes){
									if(p.getName().toLowerCase().equals(fl[0].toLowerCase())){
										l.get(k).addPotionEffect(new PotionEffect(p.getId(), Integer.parseInt(fl[1])));
									}
								}
							}
						}
					}catch(Exception e){
						System.out.println("Could not parse Buff!");
						e.printStackTrace();
					}
				}
				if(iFlag[0].toLowerCase().equals("quest")){
					try{
						potentialNextQuest = iFlag[1];
					}catch(Exception e){
						System.out.println("Could not parse Next Quest!");
						e.printStackTrace();
					}
				}
			}
			if(potentialNextQuest.length() > 0){
				nextQuest = "/okushama/humansplus/quest/"+potentialNextQuest;
				HandlerQuests.nextQuest = nextQuest;
				HandlerQuests.nextVendor = vendor;
				//System.out.println("Set next quest and Vendor!");
				return;
			}
			
		}catch(Exception e){
			System.out.println("Could not give reward!");
			e.printStackTrace();
		}
		
	}
	
	public boolean assignQuestToHuman(double withinPlayerDist){
		if(vendor != null){ return true; }
		World w = Minecraft.getMinecraft().theWorld;
		if(Minecraft.getMinecraft().getIntegratedServer() != null){
			w = Minecraft.getMinecraft().getIntegratedServer().worldServerForDimension(thePlayer.dimension);
		}
		List<Entity> ents = w.loadedEntityList;
		for(int i = 0; i < ents.size(); i++){
			if(!(ents.get(i) instanceof Human)){
				continue;
			}
			Human human = (Human)ents.get(i);
			if(human.getEntityName().toLowerCase().equals(this.npctype.toLowerCase())){
				if(!human.isVending && human.getDistanceToEntity(thePlayer) <= withinPlayerDist){
					this.vendor = human;
					engine.put("vendor", vendor);
					human.setVendingQuest(true);
					human.setName(npcname);
					//humanC.name = this.npcname;
					//System.out.println("Got a vendor! "+human.name+"  "+this.npcname);
					return true;
				}
			}
		}
		return false;
	}

	public static int COLLECTION = 0, BOUNTY = 1, SCOUT = 2, HUNTED = 3; 
}
