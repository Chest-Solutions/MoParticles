package com.csl.moparticles.molang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal MoLang evaluator for particle baking. */
public final class MolangEngine {

    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    private static final Pattern IDENT  = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*");

    public static final class Scope {
        public final Map<String, Double> variable = new HashMap<>();
        public final Map<String, Double> temp     = new HashMap<>();
        public final Map<String, Double> query    = new HashMap<>();
        public final Random rng = new Random();

        public Scope copy() {
            Scope s = new Scope();
            s.variable.putAll(this.variable);
            s.temp.putAll(this.temp);
            s.query.putAll(this.query);
            return s;
        }
    }

    /** Fast path for numeric literals. */
    public double evalLiteral(String expr) {
        if (expr == null) return 0.0;
        String s = expr.trim();
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) { }
        return eval(s, new Scope()).value;
    }

    public static final class Result {
        public final double value;
        public final boolean returned;
        public Result(double v, boolean r) { this.value = v; this.returned = r; }
        public Result(double v) { this.value = v; this.returned = false; }
    }

    private enum TT {
        NUMBER, IDENT, OP, LPAREN, RPAREN, LBRACE, RBRACE, LBRACK, RBRACK,
        COMMA, DOT, QUESTION, COLON, SEMI, RETURN, THIS, NULL_COALESCE, EOF
    }

    private static final class Token {
        final TT type;
        final String text;
        final int pos;
        Token(TT t, String txt, int p) { type = t; text = txt; pos = p; }
        public String toString() { return type + "(" + text + ")"; }
    }

    private List<Token> tokenise(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }

            if (c == '?' && i + 1 < n && src.charAt(i + 1) == '?') {
                out.add(new Token(TT.NULL_COALESCE, "??", i)); i += 2; continue;
            }

            if (c == '-' && (out.isEmpty() || isPrefixOp(out.get(out.size() - 1).type))) {
                Matcher m = NUMBER.matcher(src.substring(i));
                if (m.find() && m.start() == 0) {
                    String num = m.group();
                    out.add(new Token(TT.NUMBER, num, i));
                    i += num.length();
                    continue;
                }
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                Matcher m = NUMBER.matcher(src.substring(i));
                if (m.find() && m.start() == 0) {
                    String num = m.group();
                    out.add(new Token(TT.NUMBER, num, i));
                    i += num.length();
                    continue;
                }
            }

            if (Character.isLetter(c) || c == '_') {
                Matcher m = IDENT.matcher(src.substring(i));
                if (m.find() && m.start() == 0) {
                    String id = m.group();
                    switch (id) {
                        case "return": out.add(new Token(TT.RETURN, id, i)); break;
                        case "this":   out.add(new Token(TT.THIS,   id, i)); break;
                        default:       out.add(new Token(TT.IDENT,  id, i)); break;
                    }
                    i += id.length();
                    continue;
                }
            }

            if (i + 1 < n) {
                String two = src.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=") || two.equals("&&") || two.equals("||")) {
                    out.add(new Token(TT.OP, two, i));
                    i += 2;
                    continue;
                }
            }

            switch (c) {
                case '(': out.add(new Token(TT.LPAREN,  "(", i)); i++; continue;
                case ')': out.add(new Token(TT.RPAREN,  ")", i)); i++; continue;
                case '{': out.add(new Token(TT.LBRACE,  "{", i)); i++; continue;
                case '}': out.add(new Token(TT.RBRACE,  "}", i)); i++; continue;
                case '[': out.add(new Token(TT.LBRACK,  "[", i)); i++; continue;
                case ']': out.add(new Token(TT.RBRACK,  "]", i)); i++; continue;
                case ',': out.add(new Token(TT.COMMA,   ",", i)); i++; continue;
                case '.': out.add(new Token(TT.DOT,     ".", i)); i++; continue;
                case '?': out.add(new Token(TT.QUESTION,"?", i)); i++; continue;
                case ':': out.add(new Token(TT.COLON,   ":", i)); i++; continue;
                case ';': out.add(new Token(TT.SEMI,    ";", i)); i++; continue;
                default:
                    if ("+-*/%<>=!".indexOf(c) >= 0) {
                        out.add(new Token(TT.OP, String.valueOf(c), i));
                        i++;
                        continue;
                    }
                    i++;
            }
        }
        out.add(new Token(TT.EOF, "", n));
        return out;
    }

    private static boolean isPrefixOp(TT t) {
        return t == TT.LPAREN || t == TT.COMMA || t == TT.QUESTION
                || t == TT.COLON || t == TT.SEMI || t == TT.OP
                || t == TT.LBRACK || t == TT.LBRACE || t == TT.RETURN;
    }

    private final List<Token> tokens = new ArrayList<>();
    private int p;

    public Result eval(String src, Scope scope) {
        synchronized (tokens) {
            tokens.clear();
            tokens.addAll(tokenise(src));
            p = 0;
        }
        Result r = parseStatements(scope, false);
        return r == null ? new Result(0.0) : r;
    }

    /** Evaluates a comma-separated vector expression. */
    public List<Double> evalVector(String src, Scope scope, int count) {
        List<Double> out = new ArrayList<>(count);
        if (src == null) {
            for (int i = 0; i < count; i++) out.add(0.0);
            return out;
        }
        String s = src.trim();
        if (s.isEmpty()) {
            for (int i = 0; i < count; i++) out.add(0.0);
            return out;
        }
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        String[] parts = s.split(",");
        int idx = 0;
        for (String part : parts) {
            if (idx >= count) break;
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            out.add(eval(piece, scope).value);
            idx++;
        }
        while (out.size() < count) out.add(0.0);
        return out;
    }

    private Result parseStatements(Scope scope, boolean insideBrace) {
        double last = 0.0;
        boolean hasValue = false;
        while (!eof()) {
            Token t = peek();
            if (insideBrace && t.type == TT.RBRACE) break;
            if (t.type == TT.SEMI) { advance(); continue; }
            if (t.type == TT.RETURN) {
                advance();
                Result r = parseExpr(scope);
                return new Result(r.value, true);
            }
            Result r = parseExpr(scope);
            if (r.returned) return r;
            last = r.value;
            hasValue = true;
            if (peek().type == TT.SEMI) advance();
        }
        return hasValue ? new Result(last) : new Result(0.0);
    }

    private Result parseExpr(Scope scope) {
        if (peek().type == TT.IDENT) {
            int save = p;
            StringBuilder name = new StringBuilder();
            name.append(advance().text);
            while (peek().type == TT.DOT) {
                advance();
                if (peek().type != TT.IDENT) break;
                name.append('.').append(advance().text);
            }
            while (peek().type == TT.LBRACK) {
                advance();
                int depth = 1;
                while (depth > 0 && !eof()) {
                    if (peek().type == TT.LBRACK) depth++;
                    else if (peek().type == TT.RBRACK) depth--;
                    advance();
                }
            }
            if (peek().type == TT.OP && peek().text.equals("=")) {
                advance();
                Result rhs = parseExpr(scope);
                assignCompoundSave(name.toString(), rhs.value, scope);
                return new Result(rhs.value);
            }
            p = save;
        }
        return parseTernary(scope);
    }

    private void assignCompoundSave(String name, double value, Scope scope) {
        String lower = name.toLowerCase();
        if (lower.startsWith("variable.")) {
            scope.variable.put(name.substring("variable.".length()), value);
        } else if (lower.startsWith("temp.")) {
            scope.temp.put(name.substring("temp.".length()), value);
        } else {
            scope.variable.put(name, value);
        }
    }

    private Result parseTernary(Scope scope) {
        Result cond = parseNullCoalesce(scope);
        if (peek().type == TT.QUESTION) {
            advance();
            Result thenB = parseNullCoalesce(scope);
            if (peek().type == TT.COLON) {
                advance();
                Result elseB = parseTernary(scope);
                return new Result(cond.value != 0.0 ? thenB.value : elseB.value);
            }
            return new Result(cond.value != 0.0 ? thenB.value : 0.0);
        }
        return cond;
    }

    private Result parseNullCoalesce(Scope scope) {
        Result left = parseOr(scope);
        while (peek().type == TT.NULL_COALESCE) {
            advance();
            Result right = parseOr(scope);
            double v = left.value != 0.0 ? left.value : right.value;
            left = new Result(v);
        }
        return left;
    }

    private Result parseOr(Scope scope) {
        Result left = parseAnd(scope);
        while (peek().type == TT.OP && peek().text.equals("||")) {
            advance();
            Result right = parseAnd(scope);
            left = new Result((left.value != 0.0 || right.value != 0.0) ? 1.0 : 0.0);
        }
        return left;
    }

    private Result parseAnd(Scope scope) {
        Result left = parseEquality(scope);
        while (peek().type == TT.OP && peek().text.equals("&&")) {
            advance();
            Result right = parseEquality(scope);
            left = new Result((left.value != 0.0 && right.value != 0.0) ? 1.0 : 0.0);
        }
        return left;
    }

    private Result parseEquality(Scope scope) {
        Result left = parseComparison(scope);
        while (peek().type == TT.OP && (peek().text.equals("==") || peek().text.equals("!="))) {
            String op = peek().text; advance();
            Result right = parseComparison(scope);
            boolean eq = left.value == right.value;
            left = new Result(op.equals("==") ? (eq ? 1.0 : 0.0) : (eq ? 0.0 : 1.0));
        }
        return left;
    }

    private Result parseComparison(Scope scope) {
        Result left = parseAddSub(scope);
        while (peek().type == TT.OP && (peek().text.equals("<") || peek().text.equals(">")
                                        || peek().text.equals("<=") || peek().text.equals(">="))) {
            String op = peek().text; advance();
            Result right = parseAddSub(scope);
            boolean r;
            switch (op) {
                case "<":  r = left.value <  right.value; break;
                case ">":  r = left.value >  right.value; break;
                case "<=": r = left.value <= right.value; break;
                default:   r = left.value >= right.value; break;
            }
            left = new Result(r ? 1.0 : 0.0);
        }
        return left;
    }

    private Result parseAddSub(Scope scope) {
        Result left = parseMulDiv(scope);
        while (peek().type == TT.OP && (peek().text.equals("+") || peek().text.equals("-"))) {
            String op = peek().text; advance();
            Result right = parseMulDiv(scope);
            left = new Result(op.equals("+") ? left.value + right.value : left.value - right.value);
        }
        return left;
    }

    private Result parseMulDiv(Scope scope) {
        Result left = parseUnary(scope);
        while (peek().type == TT.OP && (peek().text.equals("*") || peek().text.equals("/") || peek().text.equals("%"))) {
            String op = peek().text; advance();
            Result right = parseUnary(scope);
            double r;
            switch (op) {
                case "*": r = left.value * right.value; break;
                case "/": r = right.value == 0 ? 0 : left.value / right.value; break;
                default:  r = right.value == 0 ? 0 : left.value % right.value; break;
            }
            left = new Result(r);
        }
        return left;
    }

    private Result parseUnary(Scope scope) {
        if (peek().type == TT.OP && peek().text.equals("-")) {
            advance();
            Result r = parseUnary(scope);
            return new Result(-r.value);
        }
        if (peek().type == TT.OP && peek().text.equals("!")) {
            advance();
            Result r = parseUnary(scope);
            return new Result(r.value == 0.0 ? 1.0 : 0.0);
        }
        return parsePrimary(scope);
    }

    private Result parsePrimary(Scope scope) {
        Token t = peek();
        switch (t.type) {
            case NUMBER: advance(); return new Result(Double.parseDouble(t.text));
            case LPAREN: {
                advance();
                Result r = parseTernary(scope);
                if (peek().type == TT.RPAREN) advance();
                return r;
            }
            case LBRACE: {
                advance();
                Result r = parseStatements(scope, true);
                if (peek().type == TT.RBRACE) advance();
                return r;
            }
            case THIS: advance(); return new Result(0.0);
            case IDENT: return parseIdentCall(scope);
            default:
                if (!eof()) advance();
                return new Result(0.0);
        }
    }

    private Result parseIdentCall(Scope scope) {
        String name = peek().text;
        advance();

        if (peek().type == TT.DOT) {
            advance();
            String member = peek().text;
            advance();
            if (peek().type == TT.LPAREN) {
                advance();
                List<Double> args = parseArgList(scope);
                if (peek().type == TT.RPAREN) advance();
                return callFunction(name, member, args, scope);
            }
            return new Result(lookupDomain(name, member, scope));
        }

        return new Result(scope.variable.getOrDefault(name, 0.0));
    }

    private List<Double> parseArgList(Scope scope) {
        List<Double> args = new ArrayList<>();
        if (peek().type == TT.RPAREN) return args;
        args.add(parseTernary(scope).value);
        while (peek().type == TT.COMMA) {
            advance();
            args.add(parseTernary(scope).value);
        }
        return args;
    }

    private double lookupDomain(String domain, String name, Scope scope) {
        switch (domain.toLowerCase()) {
            case "variable": return scope.variable.getOrDefault(name, 0.0);
            case "temp":     return scope.temp.getOrDefault(name, 0.0);
            case "query":    return scope.query.getOrDefault(name, 0.0);
            case "math":     return MathFunctions.call(name, new ArrayList<>(), scope);
            default:         return 0.0;
        }
    }

    private Result callFunction(String domain, String name, List<Double> args, Scope scope) {
        switch (domain.toLowerCase()) {
            case "math":    return new Result(MathFunctions.call(name, args, scope));
            case "query":   return new Result(QueryFunctions.call(name, args, scope));
            case "variable": {
                return new Result(scope.variable.getOrDefault(name, 0.0));
            }
            case "temp":    return new Result(scope.temp.getOrDefault(name, 0.0));
            default:        return new Result(0.0);
        }
    }

    private Token peek() { return tokens.get(p); }
    private Token advance() { Token t = tokens.get(p); if (p < tokens.size() - 1) p++; return t; }
    private boolean eof() { return peek().type == TT.EOF; }
}
