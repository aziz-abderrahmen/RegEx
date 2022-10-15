import java.util.Scanner;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Exception;

public class RegEx {
  //MACROS
  static final int CONCAT = 0xC04CA7;
  static final int ETOILE = 0xE7011E;
  static final int ALTERN = 0xA17E54;
  static final int PROTECTION = 0xBADDAD;

  static final int PARENTHESEOUVRANT = 0x16641664;
  static final int PARENTHESEFERMANT = 0x51515151;
  static final int DOT = 0xD07;
  static final String RESET = "\033[0m";
  static final String PURPLE = "\033[0;35m";

  //REGEX
  private static String regEx;
  
  //CONSTRUCTOR
  public RegEx(){}

  //MAIN
  public static void main(String arg[]) {

    Scanner scanner = new Scanner(System.in);
    System.out.print("\n>> Please enter a regEx: ");
    regEx = scanner.next();
    scanner.close();
    System.out.println(">> Parsing regEx \"" + regEx + "\".");
    System.out.println(">> ...");
    toASCII(regEx);
    RegExTree ret = null;

    ret = generateTree(ret, regEx);

    NDFAutomaton ndfa = NDFAutomaton.step2_AhoUllman(ret); // étape deux de l'algorithme
    System.out.println("Construction of the NDFA:\n\nStarting ...\n" + ndfa.toString() + "Terminated.\n");



    ArrayList<DFAutomaton> list_determ = Determination.determineAutomaton(0, ndfa);
    Determination det = new Determination(list_determ, Determination.setLast(list_determ, ndfa));
    System.out.println(det);



    ArrayList<AT> deterministicAutomaton = AT.minimizeAutomaton(det.dfaAutomaton);
    AutomatonResult automateAfterDetermination = new AutomatonResult(deterministicAutomaton,
        AT.setInitialLetter(det, deterministicAutomaton), AT.setFinalLetter(det, deterministicAutomaton),
        Determination.setLast(list_determ, ndfa));
    System.out.println(automateAfterDetermination);
    

    // le text
    ArrayList<String> text = new ArrayList<String>();
    
    try (BufferedReader br = new BufferedReader(new FileReader("Gutenberg.txt"))) {
      String sCurrentLine;
      while ((sCurrentLine = br.readLine()) != null) {
        text.add(sCurrentLine);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // split and execute search
    long startTime = System.currentTimeMillis();
    String res = search_regex(text, automateAfterDetermination, regEx);
    long endTime = System.currentTimeMillis();
    System.out.println("Execution Time " + (endTime - startTime) + " ms");
    System.out.println("Lines that contains the expression " + regEx + "\n\n" + res);
  }

  //FROM REGEX TO SYNTAX TREE
  private static RegExTree parse(String regEx) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    for (int i=0;i<regEx.length();i++) result.add(new RegExTree(charToRoot(regEx.charAt(i)),new ArrayList<RegExTree>()));
    
    return parsingResult(result);
  }
  private static int charToRoot(char c) {
    if (c=='.') return DOT;
    if (c=='*') return ETOILE;
    if (c=='|') return ALTERN;
    if (c=='(') return PARENTHESEOUVRANT;
    if (c==')') return PARENTHESEFERMANT;
    return (int)c;
  }
  private static RegExTree parsingResult(ArrayList<RegExTree> result) throws Exception {
    while (containParenthese(result)) result=processParenthese(result);
    while (containEtoile(result)) result=processEtoile(result);
    while (containConcat(result)) result=processConcat(result);
    while (containAltern(result)) result=processAltern(result);

    if (result.size()>1) throw new Exception();

    return removeProtection(result.get(0));
  }
  private static boolean containParenthese(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==PARENTHESEFERMANT || t.root==PARENTHESEOUVRANT) return true;
    return false;
  }
  private static ArrayList<RegExTree> processParenthese(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    for (RegExTree t: trees) {
      if (!found && t.root==PARENTHESEFERMANT) {
        boolean done = false;
        ArrayList<RegExTree> content = new ArrayList<RegExTree>();
        while (!done && !result.isEmpty())
          if (result.get(result.size()-1).root==PARENTHESEOUVRANT) { done = true; result.remove(result.size()-1); }
          else content.add(0,result.remove(result.size()-1));
        if (!done) throw new Exception();
        found = true;
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(parsingResult(content));
        result.add(new RegExTree(PROTECTION, subTrees));
      } else {
        result.add(t);
      }
    }
    if (!found) throw new Exception();
    return result;
  }
  private static boolean containEtoile(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==ETOILE && t.subTrees.isEmpty()) return true;
    return false;
  }
  private static ArrayList<RegExTree> processEtoile(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    for (RegExTree t: trees) {
      if (!found && t.root==ETOILE && t.subTrees.isEmpty()) {
        if (result.isEmpty()) throw new Exception();
        found = true;
        RegExTree last = result.remove(result.size()-1);
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(last);
        result.add(new RegExTree(ETOILE, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  private static boolean containConcat(ArrayList<RegExTree> trees) {
    boolean firstFound = false;
    for (RegExTree t: trees) {
      if (!firstFound && t.root!=ALTERN) { firstFound = true; continue; }
      if (firstFound) if (t.root!=ALTERN) return true; else firstFound = false;
    }
    return false;
  }
  private static ArrayList<RegExTree> processConcat(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    boolean firstFound = false;
    for (RegExTree t: trees) {
      if (!found && !firstFound && t.root!=ALTERN) {
        firstFound = true;
        result.add(t);
        continue;
      }
      if (!found && firstFound && t.root==ALTERN) {
        firstFound = false;
        result.add(t);
        continue;
      }
      if (!found && firstFound && t.root!=ALTERN) {
        found = true;
        RegExTree last = result.remove(result.size()-1);
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(last);
        subTrees.add(t);
        result.add(new RegExTree(CONCAT, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  private static boolean containAltern(ArrayList<RegExTree> trees) {
    for (RegExTree t: trees) if (t.root==ALTERN && t.subTrees.isEmpty()) return true;
    return false;
  }
  private static ArrayList<RegExTree> processAltern(ArrayList<RegExTree> trees) throws Exception {
    ArrayList<RegExTree> result = new ArrayList<RegExTree>();
    boolean found = false;
    RegExTree gauche = null;
    boolean done = false;
    for (RegExTree t: trees) {
      if (!found && t.root==ALTERN && t.subTrees.isEmpty()) {
        if (result.isEmpty()) throw new Exception();
        found = true;
        gauche = result.remove(result.size()-1);
        continue;
      }
      if (found && !done) {
        if (gauche==null) throw new Exception();
        done=true;
        ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
        subTrees.add(gauche);
        subTrees.add(t);
        result.add(new RegExTree(ALTERN, subTrees));
      } else {
        result.add(t);
      }
    }
    return result;
  }
  private static RegExTree removeProtection(RegExTree tree) throws Exception {
    if (tree.root==PROTECTION && tree.subTrees.size()!=1) throw new Exception();
    if (tree.subTrees.isEmpty()) return tree;
    if (tree.root==PROTECTION) return removeProtection(tree.subTrees.get(0));

    ArrayList<RegExTree> subTrees = new ArrayList<RegExTree>();
    for (RegExTree t: tree.subTrees) subTrees.add(removeProtection(t));
    return new RegExTree(tree.root, subTrees);
  }
  
  public static void toASCII(String regEx) {
    if (regEx.length() < 1) {
      System.err.println(">> No RegEx entered ");
    } else {
      System.out.print(">> ASCIIS: {");
      for (int i = 0; i < regEx.length(); i++)
        System.out.print("," + (int) regEx.charAt(i));
      System.out.println("}");
      System.out.println(">> : String interpretation " + regEx);
    }
  }

  public static RegExTree generateTree(RegExTree ret, String regEx) {
    try {
      regEx = regEx.replace(".", ""); // remove . concatenation
      ret = parse(regEx); // fonction parse de la string regEx
      System.out.println(">> Generated Tree : " + ret.toString());
    } catch (Exception e) {
      System.err.println(">> SYNTAX ERROR \"" + regEx + "\".");
    }
    return ret;
  }
 
  public static String search_regex(ArrayList<String> source, AutomatonResult deterministicAutomaton, String regex) {
    String res = "";
    ArrayList<String> matchLines = new ArrayList<String>();
    int cpt = 0;
    for (int i = 0; i < source.size(); i++) {
      // Tableau des mots d'une ligne
      String[] word = source.get(i).split(" ");
      // taille d'une ligne
      for (int j = 0; j < word.length; j++) {
        if (AutomatonResult.validate_regex(word[j], deterministicAutomaton, regex)) {
          matchLines.add(source.get(i));
          break;
        }
      }
    }
    for (int k = 0; k < matchLines.size(); k++) {
      String[] word = matchLines.get(k).split(" ");
      for (int w = 0; w < word.length; w++) {
        if (AutomatonResult.validate_regex(word[w], deterministicAutomaton, regex)) {
          res += PURPLE + word[w] + RESET + " ";
          cpt += 1;
        } else {
          res += word[w] + " ";
        }
        if (w == word.length - 1) {
          res += "\n";
        }
      }
    }
    System.out.println("Match result: " + cpt);
    return res;
  }
}

//UTILITARY CLASS
class RegExTree {
  protected int root;
  protected ArrayList<RegExTree> subTrees;
  public RegExTree(int root, ArrayList<RegExTree> subTrees) {
    this.root = root;
    this.subTrees = subTrees;
  }
  //FROM TREE TO PARENTHESIS
  public String toString() {
    if (subTrees.isEmpty()) return rootToString();
    String result = rootToString()+"("+subTrees.get(0).toString();
    for (int i=1;i<subTrees.size();i++) result+=","+subTrees.get(i).toString();
    return result+")";
  }
  private String rootToString() {
    if (root==RegEx.CONCAT) return ".";
    if (root==RegEx.ETOILE) return "*";
    if (root==RegEx.ALTERN) return "|";
    if (root==RegEx.DOT) return ".";
    return Character.toString((char)root);
  }
}

class NDFAutomaton {
  //IMPLICIT REPRESENTATION HERE: INIT STATE IS ALWAYS 0; FINAL STATE IS ALWAYS transitionTable.length-1
  protected int[][] transitionTable; //ASCII transition
  protected ArrayList<Integer>[] epsilonTransitionTable; //epsilon transition list
  public NDFAutomaton(int[][] transitionTable, ArrayList<Integer>[] epsilonTransitionTable) {
    this.transitionTable=transitionTable;
    this.epsilonTransitionTable=epsilonTransitionTable;
  }
  //PRINT THE AUTOMATON TRANSITION TABLE
  public String toString() {
    String result="Initial state: 0\nFinal state: "+(transitionTable.length-1)+"\nTransition list:\n";
    for (int i=0;i<epsilonTransitionTable.length;i++) for (int state: epsilonTransitionTable[i])
      result+="  "+i+" === eps ===> "+state+"\n";
    for (int i=0;i<transitionTable.length;i++) for (int col=0;col<256;col++)
      if (transitionTable[i][col]!=-1) result+="  "+i+" === "+(char)col+" ===> "+transitionTable[i][col]+"\n";
    return result;
  }

  public static NDFAutomaton step2_AhoUllman(RegExTree ret) {
    
    if (ret.subTrees.isEmpty()) {
      //IMPLICIT REPRESENTATION HERE: INIT STATE IS ALWAYS 0; FINAL STATE IS ALWAYS transitionTable.length-1
      int[][] tTab = new int[2][256];
      ArrayList<Integer>[] eTab = new ArrayList[2];
      
      //DUMMY VALUES FOR INITIALIZATION
      for (int i=0;i<tTab.length;i++) for (int col=0;col<256;col++) tTab[i][col]=-1;
      for (int i=0;i<eTab.length;i++) eTab[i]=new ArrayList<Integer>();
      
      if (ret.root!=RegEx.DOT) tTab[0][ret.root]=1; //transition ret.root from initial state "0" to final state "1"
      else for (int i=0;i<256;i++) tTab[0][i]=1; //transition DOT from initial state "0" to final state "1"
      
      return new NDFAutomaton(tTab,eTab);
    }
    
    if (ret.root==RegEx.CONCAT) {
      //IMPLICIT REPRESENTATION HERE: INIT STATE IS ALWAYS 0; FINAL STATE IS ALWAYS transitionTable.length-1
      NDFAutomaton gauche = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_g = gauche.transitionTable;
      ArrayList<Integer>[] eTab_g = gauche.epsilonTransitionTable;
      NDFAutomaton droite = step2_AhoUllman(ret.subTrees.get(1));
      int[][] tTab_d = droite.transitionTable;
      ArrayList<Integer>[] eTab_d = droite.epsilonTransitionTable;
      int lg=tTab_g.length;
      int ld=tTab_d.length;
      int[][] tTab = new int[lg+ld][256];
      ArrayList<Integer>[] eTab = new ArrayList[lg+ld];

      //DUMMY VALUES FOR INITIALIZATION
      for (int i=0;i<tTab.length;i++) for (int col=0;col<256;col++) tTab[i][col]=-1;
      for (int i=0;i<eTab.length;i++) eTab[i]=new ArrayList<Integer>();

      eTab[lg-1].add(lg); //epsilon transition from old final state "left" to old initial state "right"

      for (int i=0;i<lg;i++) for (int col=0;col<256;col++) tTab[i][col]=tTab_g[i][col]; //copy old transitions
      for (int i=0;i<lg;i++) eTab[i].addAll(eTab_g[i]); //copy old transitions
      for (int i=lg;i<lg+ld-1;i++) for (int col=0;col<256;col++) if (tTab_d[i-lg][col]!=-1) tTab[i][col]=tTab_d[i-lg][col]+lg; //copy old transitions
      for (int i=lg;i<lg+ld-1;i++) for (int s: eTab_d[i-lg]) eTab[i].add(s+lg); //copy old transitions

      return new NDFAutomaton(tTab,eTab);
    }

    if (ret.root==RegEx.ALTERN) {
      //IMPLICIT REPRESENTATION HERE: INIT STATE IS ALWAYS 0; FINAL STATE IS ALWAYS transitionTable.length-1
      NDFAutomaton gauche = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_g = gauche.transitionTable;
      ArrayList<Integer>[] eTab_g = gauche.epsilonTransitionTable;
      NDFAutomaton droite = step2_AhoUllman(ret.subTrees.get(1));
      int[][] tTab_d = droite.transitionTable;
      ArrayList<Integer>[] eTab_d = droite.epsilonTransitionTable;
      int lg=tTab_g.length;
      int ld=tTab_d.length;
      int[][] tTab = new int[2+lg+ld][256];
      ArrayList<Integer>[] eTab = new ArrayList[2+lg+ld];

      //DUMMY VALUES FOR INITIALIZATION
      for (int i=0;i<tTab.length;i++) for (int col=0;col<256;col++) tTab[i][col]=-1;
      for (int i=0;i<eTab.length;i++) eTab[i]=new ArrayList<Integer>();

      eTab[0].add(1); //epsilon transition from new initial state to old initial state
      eTab[0].add(1+lg); //epsilon transition from new initial state to old initial state
      eTab[1+lg-1].add(2+lg+ld-1); //epsilon transition from old final state to new final state
      eTab[1+lg+ld-1].add(2+lg+ld-1); //epsilon transition from old final state to new final state

      for (int i=1;i<1+lg;i++) for (int col=0;col<256;col++) if (tTab_g[i-1][col]!=-1) tTab[i][col]=tTab_g[i-1][col]+1; //copy old transitions
      for (int i=1;i<1+lg;i++) for (int s: eTab_g[i-1]) eTab[i].add(s+1); //copy old transitions
      for (int i=1+lg;i<1+lg+ld-1;i++) for (int col=0;col<256;col++) if (tTab_d[i-1-lg][col]!=-1) tTab[i][col]=tTab_d[i-1-lg][col]+1+lg; //copy old transitions
      for (int i=1+lg;i<1+lg+ld;i++) for (int s: eTab_d[i-1-lg]) eTab[i].add(s+1+lg); //copy old transitions

      return new NDFAutomaton(tTab,eTab);
    }

    if (ret.root==RegEx.ETOILE) {
      //IMPLICIT REPRESENTATION HERE: INIT STATE IS ALWAYS 0; FINAL STATE IS ALWAYS transitionTable.length-1
      NDFAutomaton fils = step2_AhoUllman(ret.subTrees.get(0));
      int[][] tTab_fils = fils.transitionTable;
      ArrayList<Integer>[] eTab_fils = fils.epsilonTransitionTable;
      int l=tTab_fils.length;
      int[][] tTab = new int[2+l][256];
      ArrayList<Integer>[] eTab = new ArrayList[2+l];

      //DUMMY VALUES FOR INITIALIZATION
      for (int i=0;i<tTab.length;i++) for (int col=0;col<256;col++) tTab[i][col]=-1;
      for (int i=0;i<eTab.length;i++) eTab[i]=new ArrayList<Integer>();

      eTab[0].add(1); //epsilon transition from new initial state to old initial state
      eTab[0].add(2+l-1); //epsilon transition from new initial state to new final state
      eTab[2+l-2].add(2+l-1); //epsilon transition from old final state to new final state
      eTab[2+l-2].add(1); //epsilon transition from old final state to old initial state

      for (int i=1;i<2+l-1;i++) for (int col=0;col<256;col++) if (tTab_fils[i-1][col]!=-1) tTab[i][col]=tTab_fils[i-1][col]+1; //copy old transitions
      for (int i=1;i<2+l-1;i++) for (int s: eTab_fils[i-1]) eTab[i].add(s+1); //copy old transitions

      return new NDFAutomaton(tTab,eTab);
    }

    return null;
  }

}

class DFAutomaton {
  protected ArrayList<Integer> line;
  protected int column;
  protected ArrayList<Integer> value;

  public DFAutomaton(ArrayList<Integer> line, int column, ArrayList<Integer> value) {
    this.line = line;
    this.column = column;
    this.value = value;
  }

  @Override
  public String toString() {
    return "\nline=" + line + ", column=" + (char) column + ", value=" + value;
  }
}

class Determination {

  protected ArrayList<DFAutomaton> dfaAutomaton;
  protected ArrayList<Integer> firstState;
  protected ArrayList<Integer> finalState;
  public static boolean moreEpsilonState;

  public Determination(ArrayList<DFAutomaton> dfaAutomaton, ArrayList<Integer> finalState) {
    this.dfaAutomaton = dfaAutomaton;
    firstState = new ArrayList<Integer>();
    firstState.add(0);
    this.finalState = finalState;
    moreEpsilonState = false;
  }

  @Override
  public String toString() {
    String res = "BEGIN DETERMINISATION :\n";
    for (int i = 0; i < dfaAutomaton.size(); i++) {
      res += dfaAutomaton.get(i);
    }
    res += "\nfirst state : ";
    for (int i = 0; i < firstState.size(); i++)
      res += firstState.get(i);
    res += "\nfinal state : ";
    for (int i = 0; i < finalState.size() - 1; i++)
      res += finalState.get(i) + ", ";
    res += finalState.get(finalState.size() - 1);
    res += "\n\nEND DETERMINISATION\n";
    return res;
  }

  /**
   * Set a list with the last elements of an automata state
   */
  public static ArrayList<Integer> setLast(ArrayList<DFAutomaton> determination, NDFAutomaton ndfaAutomaton) {
    ArrayList<Integer> finalState = new ArrayList<Integer>();
    for (int i = 0; i < determination.size(); i++) {
      if (determination.get(i).value.contains(ndfaAutomaton.epsilonTransitionTable.length - 1)) {
        finalState.add(determination.get(i).value.get(0));
      }
    }
    for (int i = 0; i < finalState.size() - 1; i++) {
      if (finalState.get(i) == (finalState.get(i + 1)))
        finalState.remove(finalState.get(i));
    }
    return finalState;
  }

  public static ArrayList<DFAutomaton> determineAutomaton(int state, NDFAutomaton ndfaAutomaton) {
    ArrayList<DFAutomaton> determinationStep1 = step3_determination(state, ndfaAutomaton);
    determinationStep1.addAll(toLoop(determinationStep1.get(0).value, ndfaAutomaton));
    int j=0;
    for (int i = 1; i < determinationStep1.size() && j < 100; i++) {
      determinationStep1.addAll(toLoop(determinationStep1.get(i).value, ndfaAutomaton));
      if (lineAndColumnAlreadyPresent(determinationStep1, determinationStep1.get(i))) {
        j++;
      }
    }
      return removeDoubleValues(determinationStep1);
  }

  private static boolean isEqualDFAutomaton(DFAutomaton a, DFAutomaton b) {
    return a.line.equals(b.line) && a.column == b.column && a.value.equals(b.value);
  }

  private static int findIndexOfDoubleValue(ArrayList<DFAutomaton> doublons) {
    boolean found = false;
    for (int i = 0; i < doublons.size() && !found; i++) {
      for (int j = 0; j < doublons.size(); j++) {
        if (i == j)
          continue;
        if (isEqualDFAutomaton(doublons.get(i), doublons.get(j)))
          return j;
      }
    }
    return -1;
  }

  private static ArrayList<DFAutomaton> removeDoubleValues(ArrayList<DFAutomaton> doublons) {
    ArrayList<DFAutomaton> res = new ArrayList<DFAutomaton>();
    if (findIndexOfDoubleValue(doublons) != -1) {
      for (int i = 0; i < findIndexOfDoubleValue(doublons); i++) {
        res.add(doublons.get(i));
      }
    }
    if(res.isEmpty()) {
      return doublons;
    }
    return res;
  }

  private static boolean lineAndColumnAlreadyPresent(ArrayList<DFAutomaton> dfa, DFAutomaton actualDfa) {

    for (DFAutomaton dfaArray : dfa) {
      if (dfaArray.equals(actualDfa)) {
        return true;
      }
    }
    return false;
  }

  public static ArrayList<DFAutomaton> step3_determination(int state, NDFAutomaton ndfaAutomaton) {
    // dans l'étape 1 la variable state est 0
    if (!findOccurenceEpsArray(state, ndfaAutomaton.epsilonTransitionTable).isEmpty()) {
      // etape 1
      ArrayList<Integer> listATraiter = findOccurenceEpsArray(state, ndfaAutomaton.epsilonTransitionTable);
      if (needMoreEps(listATraiter, ndfaAutomaton)) {
        ArrayList<Integer> newList = aggiungiEps(listATraiter, ndfaAutomaton.epsilonTransitionTable);
        if (state == 0)
          newList.add(0, 0);
        moreEpsilonState = true;
        return toLoop(newList, ndfaAutomaton);
      }
      if (state == 0)
        listATraiter.add(0, 0);
      return toLoop(listATraiter, ndfaAutomaton);
    }
    ArrayList<Integer> listATraiter = new ArrayList<Integer>();
    listATraiter.add(0);
    return toLoop(listATraiter, ndfaAutomaton);

  }

  public static boolean needMoreEps(ArrayList<Integer> listATraiter, NDFAutomaton ndfaAutomaton) {
    ArrayList<Integer>[] epsilonTable = ndfaAutomaton.epsilonTransitionTable;
    for (int i = 0; i < listATraiter.size(); i++) {
      if (!epsilonTable[listATraiter.get(i)].isEmpty()) {
        return true;
      }
    }
    return false;
  }

  public static ArrayList<Integer> addEpsFirstStep(ArrayList<Integer> listATraiter, NDFAutomaton ndfaAutomaton) {
    ArrayList<Integer> res = new ArrayList<Integer>();
    res.addAll(listATraiter);
    ArrayList<Integer>[] epsilonTable = ndfaAutomaton.epsilonTransitionTable;

    while (!listATraiter.isEmpty()) {

      for (int i = 0; i < listATraiter.size(); i++) {
        if (!epsilonTable[listATraiter.get(i)].isEmpty()) {

          listATraiter.addAll(epsilonTable[listATraiter.get(i)]);
          res.addAll(epsilonTable[listATraiter.get(i)]);
          listATraiter.remove(i);
        } else {
          try {
            listATraiter.remove(i);
          } catch (Exception e) {
          }
        }
      }
    }
    return res;
  }

  private static ArrayList<Integer> aggiungiEps(ArrayList<Integer> listATraiter,
      ArrayList<Integer>[] epsilonTable) {
    for (int i = 0; i < listATraiter.size(); i++) {
      if (!epsilonTable[listATraiter.get(i)].isEmpty()) {
        listATraiter.addAll(epsilonTable[listATraiter.get(i)]);
      }
    }
    return listATraiter;
  }

  public static ArrayList<DFAutomaton> toLoop(ArrayList<Integer> listATraiter, NDFAutomaton ndfaAutomaton) {
    ArrayList<DFAutomaton> res = new ArrayList<DFAutomaton>();
    for (int i = 0; i < listATraiter.size(); i++) {
      int currentElementList = listATraiter.get(i);
      DFAutomaton verify = setCase(currentElementList, ndfaAutomaton, listATraiter);
      if (verify.column != -1)
        res.add(setCase(currentElementList, ndfaAutomaton, listATraiter));
    }
    return res;
  }

  public static DFAutomaton setCase(int currentElementList, NDFAutomaton ndfaAutomaton, ArrayList<Integer> listLine) {
    ArrayList<Integer> res = new ArrayList<Integer>();
    int coloumn = getArrayIndexColoumnNumber(currentElementList, ndfaAutomaton.transitionTable);
    if (coloumn != -1) {
      int value = getTransitionValue(currentElementList, coloumn, ndfaAutomaton.transitionTable);
      res = addEps(value, ndfaAutomaton);
      return new DFAutomaton(listLine, coloumn, res);
    }
    return new DFAutomaton(listLine, coloumn, res);
  }

  public static ArrayList<Integer> addEps(int nb, NDFAutomaton ndfaAutomaton) {
    ArrayList<Integer> listATraiter = new ArrayList<Integer>();
    ArrayList<Integer> res = new ArrayList<Integer>();
    listATraiter.add(nb);
    res.add(nb);
    boolean continua = true;
    while (continua == true) {
      for (int i = 0; i < listATraiter.size(); i++) {
        if (findOccurenceEpsArray(listATraiter.get(i), ndfaAutomaton.epsilonTransitionTable).isEmpty()) {
          continua = false;
          continue;
        }
        res.addAll(findOccurenceEpsArray(listATraiter.get(i), ndfaAutomaton.epsilonTransitionTable));
        continua = true;
        listATraiter = res;
      }
    }
    return res;
  }

  private static int getArrayIndexColoumnNumber(int occurence, int[][] transitionTable) {
    for (int col = 0; col < 256; col++) {
      if (transitionTable[occurence][col] != -1)
        return col;
    }
    return -1;
  }


  private static int getTransitionValue(int lines, int col, int[][] transitionTable) {
    return transitionTable[lines][col];
  }


  private static ArrayList<Integer> findOccurenceEpsArray(int occurence, ArrayList<Integer>[] transitionTable) {
    return transitionTable[occurence];
  }
}

class AT {
  protected int line;
  protected int column;
  protected int value;

  public AT(int line, int column, int value) {
    this.line = line;
    this.column = column;
    this.value = value;
  }

  @Override
  public String toString() {
    return "\n"+ line + " ===> " + (char) column + " ===> " + value;
  }

  public static ArrayList<AT> minimizeAutomaton (ArrayList<DFAutomaton> deterministicAutomaton) {
    ArrayList<AT> states = new ArrayList<AT>();
    for(int i = 0; i < deterministicAutomaton.size(); i++) {
      int line = deterministicAutomaton.get(i).line.get(0);
      int column = deterministicAutomaton.get(i).column;
      int value = deterministicAutomaton.get(i).value.get(0);
      states.add(new AT(line,column,value));
    }
    return states;
  }

  public static ArrayList<Integer> setInitialLetter (Determination det,ArrayList<AT> deterministicAutomaton) {
    ArrayList<Integer> firstState = det.firstState;
    ArrayList<Integer> initialsLetter = new ArrayList<Integer>();
    for(int i = 0; i<deterministicAutomaton.size(); i++) {
      if(!initialsLetter.contains(deterministicAutomaton.get(i).column)) {
        if(firstState.contains(deterministicAutomaton.get(i).line)) 
          initialsLetter.add(deterministicAutomaton.get(i).column);
      }
    }
    return initialsLetter;   
  } 
  public static ArrayList<Integer> setFinalLetter (Determination det,ArrayList<AT> deterministicAutomaton) {
    ArrayList<Integer> finalState = det.finalState;
    ArrayList<Integer> finalLetter = new ArrayList<Integer>();
    for(int i = 0; i<deterministicAutomaton.size(); i++) {
      if(!finalLetter.contains(deterministicAutomaton.get(i).column)) {
        if(finalState.contains(deterministicAutomaton.get(i).value)) 
          finalLetter.add(deterministicAutomaton.get(i).column);
      }
    }
    return finalLetter;
  }
} 

class AutomatonResult {

  protected ArrayList<AT> regEx_automaton;
  protected ArrayList<Integer> initialLEtters;
  protected ArrayList<Integer> finalLetters;

  protected int firstState;
  protected ArrayList<Integer> finalState;

  public AutomatonResult(ArrayList<AT> regEx_automaton, ArrayList<Integer> initialLEtters,
      ArrayList<Integer> finalLetters, ArrayList<Integer> finalState) {
    this.regEx_automaton = regEx_automaton;
    this.initialLEtters = initialLEtters;
    this.finalLetters = finalLetters;
    firstState = 0;
    this.finalState = finalState;
  }

  @Override
  public String toString() {
    String res = " Start Minimisation :\n";
    for (int i = 0; i < regEx_automaton.size(); i++) {
      res += regEx_automaton.get(i);
    }
    res += "\n initial states : " + firstState;
    res += "\n final states : " + finalState;
    res += "\n\n Minimised Automaton\n";
    return res;
  }

  // Function to convert String
  // to ArrayList of Characters
  private static ArrayList<Character> convertStringToCharArrayList(String str) {
    ArrayList<Character> chars = new ArrayList<Character>();
    for (char ch : str.toCharArray()) {
      chars.add(ch);
    }
    return chars;
  }

  public static boolean validate_regex(String wordToValidate, AutomatonResult deterministicAutomaton, String regEx) {

    ArrayList<Character> word = convertStringToCharArrayList(wordToValidate);
    int initialState = deterministicAutomaton.firstState;
    boolean found = false;

    // Check if any letter are not present in automate
    for (int i = 0; i < word.size() && !found; i++) {
      if (findLetterInAutomaton((int) word.get(i), deterministicAutomaton))
        found = true;
    }
    if (!found)
      return false;

    found = false;

    int i = 0;
    while (!found && !word.isEmpty() && i < word.size()) {
      // check if the letter is the fist of occurences
      if (!isAutorizhedLetter((int) word.get(i), initialState, deterministicAutomaton)) {
        word.remove(i);
        ArrayList<Character> word1=subword(word,i);
        word.clear();
        word=word1;
        initialState =0;
        continue;
      }
      // check if the letter is also the final state
      if (isFinalLetter((int) word.get(i), initialState, deterministicAutomaton)) {
        found = true;
      }
      initialState = indexAutorizhedLetter((int) word.get(i), initialState, deterministicAutomaton);
      i++;
    }
    return found;
  }
  
  private static ArrayList<Character> subword (ArrayList<Character> word,int index) {
    ArrayList<Character> res = new ArrayList<Character>();
    for(int i = index+1;i<word.size();i++){
      res.add(word.get(i));
    }
    return res;
  }

  private static boolean findLetterInAutomaton(int letter, AutomatonResult automataton) {
    ArrayList<AT> automatonList = automataton.regEx_automaton;
    for (AT automatonOccurence : automatonList) {
      if (automatonOccurence.column == letter)
        return true;
    }
    return false;
  }

  private static boolean isAutorizhedLetter(int letterToFind, int initialState, AutomatonResult automataton) {
    ArrayList<AT> automatonList = automataton.regEx_automaton;
    for (AT automatonOccurence : automatonList) {
      if (automatonOccurence.column == letterToFind && automatonOccurence.line == initialState)
        return true;
    }
    return false;
  }

  private static int indexAutorizhedLetter(int letterToFind, int initialState, AutomatonResult automataton) {
    ArrayList<AT> automatonList = automataton.regEx_automaton;
    for (int i = 0; i < automatonList.size(); i++) {
      if (automatonList.get(i).column == letterToFind && automatonList.get(i).line == initialState)
        return automatonList.get(i).value;
    }
    return -1;
  }

  private static boolean isFinalLetter(int letterToFind, int initialState, AutomatonResult automataton) {
    ArrayList<Integer> finalState = automataton.finalState;
    ArrayList<AT> automatonList = automataton.regEx_automaton;
    for (AT automatonOccurence : automatonList) {
      if (automatonOccurence.column == letterToFind && automatonOccurence.line == initialState
          && finalState.contains(automatonOccurence.value))
        return true;
    }
    return false;
  }
}


