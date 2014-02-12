package com.nixus.raop.core;

import java.util.*;
import java.io.*;

/**
 * A Utility class that can be used to determine if an Object matches a specified filter.
 * The filter is made up of multiple tests, each one taking one of the following forms
 *
 * <table>
 * <tr><th>key = value</th><td>The specified field equals the value, eg "type = 'mp3'"</td></tr>
 * <tr><th>key != value</th><td>The specified field doesn't equal the value, eg "type != 'mp3'"</td></tr>
 * <tr><th>key &lt; value</th><td>The specified field is less than the value, eg "year < 2000"</td></tr>
 * <tr><th>key &gt; value</th><td>The specified field is greater than the value, eg "year > 2000"</td></tr>
 * <tr><th>key &lt;= value</th><td>The specified field is less than or equal to the value, eg "year <= 2000"</td></tr>
 * <tr><th>key &gt;= value</th><td>The specified field is greater than or equal to the value, eg "year >= 2000"</td></tr>
 * <tr><th>key =~ value</th><td>The specified field matches the value regex, eg "title =~ '.*King.*'"</td></tr>
 * <tr><th>key !~ value</th><td>The specified field doesn't match the value regex, eg "album !~ '^The'"</td></tr>
 * </table>
 * Each individual test can be linked using "or" or "and", and these can be grouped using brackets.
 *
 * Subclasses need to implement the {@link #getProperty} method, which returns the named property from the
 * object being tested. 
 */
public abstract class AbstractMatcher<E> implements Serializable {

    private String filter;
    private transient List<Object> test;

    /**
     * Create a new AbstractMatcher
     * @param filter the filter String
     */
    public AbstractMatcher(String filter) {
        filter = filter.trim();
        if (filter==null || filter.equals("*")) {
            this.filter = "*";
            test = null;
        } else {
            this.filter = filter;
            this.test = new ArrayList<Object>(5);
            Stack<Object> stack = new Stack<Object>();
            char lastc = 0;
            int lasti = 0, quotestart = -1, quotechar = 0;
            String key = null, val = null, op = null, join = null;
            filter = filter.trim();
            for (int i=0;i<=filter.length();i++) {
                char c = i==filter.length() ? 0 : filter.charAt(i);
                if (c=='\\') {
                    c = filter.charAt(++i);
                    if (c=='n') {
                        c = '\n';
                    } else if (c=='r') {
                        c = '\r';
                    } else if (c=='t') {
                        c = '\t';
                    }
                } else if ((c=='\'' || c=='"') && quotechar==c) {
                    quotechar = 0;
                } else if (quotechar==0 && ((i > 0 && Character.isLetterOrDigit(c) && !Character.isLetterOrDigit(lastc)) || "()!=<>~'\"\u0000".indexOf(c)>=0)) {
                    int end = i;
                    while (lastc==' ') {
                        end--;
                        lastc = filter.charAt(end-1);
                    }
                    String token = filter.substring(lasti, end);
//                    System.out.println("TOKEN=\""+token+"\" key="+key+" op="+op+" val="+val+" join="+join+" i="+i+" c="+c);
                    lasti = i;
                    if (c=='\'' || c=='"') {
                        quotechar = c;
                    }
                    if (token.length()==0 && i==0) {
                        //
                    } else if (token.equals("(")) {
                        stack.push("(");
                    } else if (token.equals(")")) {
                        Object o;
                        while (!(o=stack.pop()).equals("(")) {
                            test.add(o);
                        }
                    } else if (key==null) {
                        if (token.length() > 1 && (token.charAt(0)=='\'' || token.charAt(0)=='"') && token.charAt(0)==token.charAt(token.length()-1)) {
                            token = token.substring(1, token.length()-1);
                        }
                        key = token;
                        join = null;
                    } else if (op==null) {
                        op = token;
                    } else if (val==null) {
                        if (token.equals("=") || token.equals("~")) {
                            op += token;
                        } else {
                            if (token.length() > 1 && (token.charAt(0)=='\'' || token.charAt(0)=='"') && token.charAt(0)==token.charAt(token.length()-1)) {
                                token = token.substring(1, token.length()-1);
                            }
                            val = token;
                            test.add(new Test(key, op, val));
                        }
                    } else if (join==null) {
                        boolean and = "and".equalsIgnoreCase(token);
                        if (and || "or".equalsIgnoreCase(token)) {
                            while (true) {
                                String lastop = stack.isEmpty() ? "(" : (String)stack.peek();
                                if (lastop.equals("(") || (lastop.equals("OR") && and)) {
                                    stack.push(and ? "AND" : "OR");
                                    break;
                                } else {
                                    test.add(stack.pop());
                                }
                            }
                        } else {
                            throw new IllegalArgumentException("Invalid token \""+token+"\" in \""+filter+"\"");
                        }
                        key = op = val = null;
                    }
                }
                lastc = c;
            }
            while (!stack.isEmpty()) {
                test.add(stack.pop());
            }
            if (test.isEmpty()) {
                throw new IllegalStateException("Invalid filter \""+filter+"\"");
            }
        }
    }

    /**
     * Return the specified Property from the source object being tested
     */
    public abstract Object getProperty(E source, String property);

    /**
     * Return true if this AbstractMatcher matches the specified object
     */
    @SuppressWarnings("unchecked")
    public boolean matches(E source) {
        if (test==null) {
            return true;
        }
        Stack<String> work = new Stack<String>();
        for (int i=0;i<test.size();i++) {
            Object o = test.get(i);
            if (o.equals("OR")) {
                Object o1 = work.pop();
                Object o2 = work.pop();
                work.push(o1.equals("1") || o2.equals("1") ? "1" : "0");
            } else if (o.equals("AND")) {
                Object o1 = work.pop();
                Object o2 = work.pop();
                work.push(o1.equals("1") && o2.equals("1") ? "1" : "0");
            } else {
                work.push(((Test)o).matches(source) ? "1" : "0");
            }
        }
        return work.pop().equals("1");
    }

    /**
     * Compare two values
     * @param key the key the values are stored under
     * @param op the operation, one of "=", "!=", "<", ">", "<=", ">=", "=~" or "!~"
     * @param testval the value specified in the test
     * @param propval the property value that we're testing, as returned from {@link #getProperty}
     */
    @SuppressWarnings("unchecked")
    public boolean test(String key, String op, String testval, Object propval) {
        Comparable propvalue = propval==null ? "" : propval.toString();
        Comparable testvalue = testval;
        if (((String)propvalue).length() > 0 && testval.length() > 0 && Character.isDigit(((String)propvalue).charAt(0)) && Character.isDigit(testval.charAt(0))) {
            try {
                if (((String)propvalue).indexOf(".") >= 0 || testval.indexOf(".") >= 0) {
                    float ipropvalue = Float.parseFloat((String)propvalue);
                    float itestvalue = Float.parseFloat((String)testvalue);
                    propvalue = Float.valueOf(ipropvalue);
                    testvalue = Float.valueOf(itestvalue);
                } else {
                    int ipropvalue = Integer.parseInt((String)propvalue);
                    int itestvalue = Integer.parseInt((String)testvalue);
                    propvalue = Integer.valueOf(ipropvalue);
                    testvalue = Integer.valueOf(itestvalue);
                }
            } catch (Exception e) { }
        }

        boolean out;
        if (op.equals("=")) {
            out = testvalue.equals(propvalue);
        } else if (op.equals("!=")) {
            out = !testvalue.equals(propvalue);
        } else if (op.equals("<")) {
            out = testvalue.compareTo(propvalue) > 0;
        } else if (op.equals("<=")) {
            out = testvalue.compareTo(propvalue) >= 0;
        } else if (op.equals(">=")) {
            out = testvalue.compareTo(propvalue) <= 0;
        } else if (op.equals(">")) {
            out = testvalue.compareTo(propvalue) < 0;
        } else if (op.equals("=~")) {
            out = propvalue.toString().matches(testvalue.toString());
        } else {
            out = !propvalue.toString().matches(testvalue.toString());
        }
//        System.out.println("THIS="+this+" prop="+propvalue+" value="+testvalue+" match="+out);
        return out;
    }

    public int hashCode() {
        return filter.hashCode();
    }

    public boolean equals(Object o) {
        return o.getClass()==getClass() ? ((AbstractMatcher)o).filter.equals(filter) : false;
    }

    private class Test {

        private final String key, val, op;

        Test(String key, String op, String val) {
            if (op.equals("=") || op.equals("!=") || op.equals(">") || op.equals(">=") || op.equals("<=") || op.equals("<") || op.equals("=~") || op.equals("!~")) {
                this.key = key;
                this.val = val;
                this.op = op;
            } else {
                throw new IllegalArgumentException("Unknown operator \""+op+"\"");
            }
        }

        public String toString() {
            return key+op+val;
        }

        boolean matches(E source) {
            Object propvalue = getProperty(source, key);
            if (propvalue==null) {
                propvalue = "";
            }
            return test(key, op, val, propvalue);
        }
    }

    public String toString() {
        return "\""+filter+"\"";
    }

    /**
     * Return the Filter String for this Matcher
     */
    public final String getFilter() {
        return filter;
    }

    /**
     * Return true if the Filter will match anything
     */
    public boolean isUniversal() {
        return filter.equals("*");
    }

}
