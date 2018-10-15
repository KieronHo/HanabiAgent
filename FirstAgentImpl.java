package agents;

import hanabAI.*;
import java.util.*;

public class FirstAgentImpl implements Agent {
	
	private HashMap<Colour, int[]> seenCards;
	private HashMap<Integer, List<Colour>> colourHintRec = new HashMap<>();
	private HashMap<Integer, List<Integer>> valueHintRec = new HashMap<>();
	private HashMap<Integer, List<Colour>> colourHintRecPrev;
	private HashMap<Integer, List<Integer>> valueHintRecPrev;
	private Colour[] colours;
	private int[] values;
	private boolean firstAction = true;
	private int numPlayers;
	private int index;
	private int[] age;
	
	/**
	 * Reports the agents name
	 * */
	public String toString(){
		return "FirstAgent";
	}

	/**
	 * Given the state, return the action that the strategy chooses for this state.
	 *
	 * @param s
	 * @return the action the agent chooses to perform
	 */
	public Action doAction(State s) {
		if(firstAction){
			init(s);
		}
		for (int i = 0; i < age.length; i++) {
			age[i] += 1;
		}
		seenCards(s);
		getHints(s);
		hintsGiven(s);
		Action a = null;
		try {
			a = playKnown(s);
			//TODO: if greater than 1 life and last round play at 0.2 probabilty
			if (a == null && s.getHintTokens() > 0) a = hintObviouslyDiscardable(s);
			if (a == null && s.getHintTokens() > 0) a = hintPlayable(s);
			if (a == null && s.getHintTokens() > 0) a = hintMostInfo(s);
			if (a == null && s.getHintTokens() < 8) a = discardKnown(s);
			//TODO: play at 0.7 probability
			if (a == null && s.getHintTokens() < 8) a = discardOldest(s);
		} catch (IllegalActionException e) {
			e.printStackTrace();
		}
		return a;
	}

	/**
	 * Default constructor, does nothing.
	 * **/
	public FirstAgentImpl(){}
	
	//Helper methods
	
	/**
	 * Initialises variables on the first call to do action.
	 * @param s the State of the game at the first action
	 **/
	public void init(State s){
		numPlayers = s.getPlayers().length;
		if(numPlayers>3){
			colours = new Colour[4];
			values = new int[4];
			age = new int[4];
		}
		else{
			colours = new Colour[5];
			values = new int[5];
			age = new int[5];
		}
		index = s.getNextPlayer();
		for (Colour c : Colour.values()) {
			seenCards.put(c, new int[6]);
		}
		for (int i = 0; i < numPlayers; i++) {
			if (i == index) continue;
			for (Card c: s.getHand(i)) {
				int[] temp = seenCards.get(c.getColour());
				temp[c.getValue()] += 1;
				seenCards.put(c.getColour(), temp);
			}
		}
		firstAction = false;
	}
	
	public void seenCards(State s){
		State t = (State) s.clone();
		for(int i = 0; i<Math.min(numPlayers-1,s.getOrder());i++) {
			Action a = t.getPreviousAction();
			if (a.getType() == ActionType.DISCARD || a.getType() == ActionType.PLAY){
				Card[] diff = compareHands(t.getPreviousState().getHand(a.getPlayer()), s.getHand(a.getPlayer()));
				for (Card c: diff) {
					int[] temp = seenCards.get(c.getColour());
					temp[c.getValue()] += 1;
					seenCards.put(c.getColour(), temp);
				}
			}
			t = t.getPreviousState();
		}
	}
	
	public void hintsGiven(State s) {
		colourHintRecPrev = colourHintRec;
		colourHintRec = new HashMap<>();
		valueHintRecPrev = valueHintRec;
		valueHintRec = new HashMap<>();
		State t = (State) s.clone();
		for (int i = 0; i < Math.min(numPlayers - 1, s.getOrder()); i++) {
			Action a = t.getPreviousAction();
			if (a.getType()== ActionType.HINT_COLOUR){
				try {
					colourHintRec.computeIfAbsent(a.getHintReceiver(), k -> new ArrayList<>()).add(a.getColour());
				} catch (IllegalActionException e) {
					e.printStackTrace();
				}
			}
			if (a.getType() == ActionType.HINT_VALUE){
				try {
					valueHintRec.computeIfAbsent(a.getHintReceiver(), k -> new ArrayList<>()).add(a.getValue());
				} catch (IllegalActionException e) {
					e.printStackTrace();
				}
			}
			t = t.getPreviousState();
		}
	}
	
	//updates colours and values from hints received
	public void getHints(State s){
		try{
			State t = (State) s.clone();
			for(int i = 0; i<Math.min(numPlayers-1,s.getOrder());i++){
				Action a = t.getPreviousAction();
				if((a.getType()== ActionType.HINT_COLOUR || a.getType() == ActionType.HINT_VALUE) && a.getHintReceiver()==index){
					boolean[] hints = t.getPreviousAction().getHintedCards();
					for(int j = 0; j<hints.length; j++){
						if(hints[j]){
							if(a.getType()==ActionType.HINT_COLOUR)
								colours[j] = a.getColour();
							else
								values[j] = a.getValue();
						}
					}
				}
				t = t.getPreviousState();
			}
		}
		catch(IllegalActionException e){e.printStackTrace();}
	}
	
	//returns the value of the next playable card of the given colour
	public int playable(State s, Colour c){
		java.util.Stack<Card> fw = s.getFirework(c);
		if (fw.size()==5) return -1;
		else return fw.size()+1;
	}
	
	
	public int lowestValue(State s) {
		int lowest = 5;
		for (Colour c : Colour.values()) {
			if (playable(s, c) != -1) {
				if (playable(s, c)-1 < lowest) {
					lowest = playable(s, c)-1;
				}
			}
		}
		return lowest;
	}
	
	//plays the first card known to be playable.
	public Action playKnown(State s) throws IllegalActionException{
		for(int i = 0; i<colours.length; i++){
			if(colours[i]!=null && values[i]==playable(s,colours[i])){
				colours[i] = null;
				values[i] = 0;
				age[i] = 0;
				return new Action(index, toString(), ActionType.PLAY,i);
			}
		}
		return null;
	}
	
	//discards the first card known to be unplayable.
	public Action discardKnown(State s) throws IllegalActionException{
		if (s.getHintTokens() != 8) {
			for(int i = 0; i<colours.length; i++){
				if(colours[i]!=null && values[i]>0 && values[i]<playable(s,colours[i])){
					colours[i] = null;
					values[i] = 0;
					age[i] = 0;
					return new Action(index, toString(), ActionType.DISCARD,i);
				}
			}
		}
		return null;
	}
	
	public Action discardOldest(State s) throws IllegalActionException{
		int oldest = 0;
		int oldestCard = 0;
		for (int i = 0; i < age.length; i++) {
			if (age[i] > oldest){
				oldest = age[i];
				oldestCard = i;
			}
		}
		return new Action(index, toString(), ActionType.DISCARD, oldestCard);
	}
	
	public Action playProbablySafe(State s, double prob){
		//TODO:
		return null;
	}
	
	public Card[] compareHands(Card[] pre, Card[] cur){
		if (Arrays.equals(pre, cur)) return new Card[5];
		List<Card> temp = Arrays.asList(cur);
		temp.removeAll(Arrays.asList(pre));
		return (Card[]) temp.toArray();
	}
	
	public Action hintMostInfo(State s) throws IllegalActionException {
		int playerToHint = 0;
		Colour maxColour = Colour.BLUE;
		int maxColourAm = 0;
		int[] maxValue = {0, 0};
		for (int i = 0; i < numPlayers; i++) {
			if (i == index) continue;
			HashMap<Colour, Integer> maxColourMap = new HashMap<>();
			HashMap<Integer, Integer> maxIntMap = new HashMap<>();
			Card[] hand = s.getHand(i);
			for (Card c : hand){
				maxColourMap.put(c.getColour(), maxColourMap.getOrDefault(c.getColour(), 0)+1);
				maxIntMap.put(c.getValue(), maxIntMap.getOrDefault(c.getValue(), 0)+1);
			}
			for (Colour c : maxColourMap.keySet()){
				if (maxColourMap.get(c) > maxColourAm && !colourHintAlreadyGiven(c,i)){
					maxColour = c;
					maxColourAm = maxColourMap.get(c);
					playerToHint = i;
				}
			}
			for (int v : maxIntMap.keySet()){
				if (maxIntMap.get(v) > maxValue[1] && !valueHintAlreadyGiven(v,i)){
					maxValue[0] = v;
					maxValue[1] = maxIntMap.get(v);
					playerToHint = i;
				}
			}
		}
		if (maxValue[1] > maxColourAm){
			return new Action(index, toString(), ActionType.HINT_VALUE, playerToHint, allValueInHand(s.getHand(playerToHint),maxValue[0]), maxValue[0]);
		} else {
			return new Action(index, toString(), ActionType.HINT_COLOUR, playerToHint, allColourInHand(s.getHand(playerToHint),maxColour), maxColour);
		}
	}
	
	public Action hintPlayable(State s) throws IllegalActionException {
		for (int i = 0; i < numPlayers; i++) {
			if (i == index) continue;
			Card[] hand = s.getHand(i);
			for (Card c : hand) {
				if (playable(s, c.getColour()) == c.getValue()){
					if (!colourHintAlreadyGiven(c.getColour(),i)){
						return new Action(index, toString(), ActionType.HINT_COLOUR, i, allColourInHand(hand,c.getColour()), c.getColour());
					}
					if (!valueHintAlreadyGiven(c.getValue(),i)){
						return new Action(index, toString(), ActionType.HINT_VALUE, i, allValueInHand(hand,c.getValue()), c.getValue());
					}
				}
			}
		}
		return null;
	}
	
	public Action hintObviouslyDiscardable(State s) throws IllegalActionException {
		for (int i = 0; i < numPlayers; i++) {
			if (i==index) continue;
			Card[] hand = s.getHand(i);
			for (Card c : hand){
				if (playable(s,c.getColour()) == -1 && !colourHintAlreadyGiven(c.getColour(),i)){
					return new Action(index, toString(), ActionType.HINT_COLOUR, i, allColourInHand(hand,c.getColour()), c.getColour());
				}
				if (c.getValue() <= lowestValue(s) && !valueHintAlreadyGiven(c.getValue(),i)){
					return new Action(index, toString(), ActionType.HINT_VALUE, i, allValueInHand(hand,c.getValue()), c.getValue());
				}
			}
		}
		return null;
	}
	
	public boolean[] allValueInHand(Card[] hand, int value){
		boolean[] cards = new boolean[5];
		for (int j = 0; j < 5; j++) {
			if (hand[j].getValue() == value){
				cards[j] = true;
			}
		}
		return cards;
	}
	public boolean[] allColourInHand(Card[] hand, Colour colour){
		boolean[] cards = new boolean[5];
		for (int j = 0; j < 5; j++) {
			if (hand[j].getColour() == colour){
				cards[j] = true;
			}
		}
		return cards;
	}
	public boolean colourHintAlreadyGiven(Colour c, int player){
		return (colourHintRec.getOrDefault(player, new ArrayList<>()).contains(c) || colourHintRecPrev.getOrDefault(player, new ArrayList<>()).contains(c));
	}
	public boolean valueHintAlreadyGiven(int value, int player){
		return (valueHintRec.getOrDefault(player, new ArrayList<>()).contains(value) || valueHintRecPrev.getOrDefault(player, new ArrayList<>()).contains(value));
	}
}