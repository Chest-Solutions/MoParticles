package com.csl.moparticles.molang;

import java.util.List;

/**
 * Built-in MoLang math.* functions.
 */
public final class MathFunctions {

    public static double call(String name, List<Double> args, MolangEngine.Scope scope) {
        switch (name) {
            case "abs":       return Math.abs(a(args, 0));
            case "floor":     return Math.floor(a(args, 0));
            case "round":     return Math.round(a(args, 0));
            case "ceil":      return Math.ceil(a(args, 0));
            case "trunc":     return (long) a(args, 0);
            case "sin":       return Math.sin(Math.toRadians(a(args, 0)));
            case "cos":       return Math.cos(Math.toRadians(a(args, 0)));
            case "tan":       return Math.tan(Math.toRadians(a(args, 0)));
            case "asin":      return Math.toDegrees(Math.asin(a(args, 0)));
            case "acos":      return Math.toDegrees(Math.acos(a(args, 0)));
            case "atan":      return Math.toDegrees(Math.atan(a(args, 0)));
            case "atan2":     return Math.toDegrees(Math.atan2(a(args, 0), a(args, 1)));
            case "sqrt":      return Math.sqrt(a(args, 0));
            case "pow":       return Math.pow(a(args, 0), a(args, 1));
            case "exp":       return Math.exp(a(args, 0));
            case "ln":        return Math.log(a(args, 0));
            case "log":       return a(args, 1) == 0 ? Math.log(a(args, 0)) : Math.log(a(args, 0)) / Math.log(a(args, 1));
            case "min":       return Math.min(a(args, 0), a(args, 1));
            case "max":       return Math.max(a(args, 0), a(args, 1));
            case "clamp":     return clamp(a(args, 0), a(args, 1), a(args, 2));
            case "lerp":      return lerp(a(args, 0), a(args, 1), a(args, 2));
            case "inverse_lerp": return invLerp(a(args, 0), a(args, 1), a(args, 2));
            case "remap": {
                // remap(value, in_min, in_max, out_min, out_max)
                double v   = a(args, 0);
                double imn = a(args, 1);
                double imx = a(args, 2);
                double omn = a(args, 3);
                double omx = a(args, 4);
                return lerp(omn, omx, invLerp(imn, imx, v));
            }
            case "saturate":  return clamp(a(args, 0), 0, 1);
            case "random": {
                if (args.size() == 1) return scope.rng.nextDouble() * a(args, 0);
                if (args.size() >= 2) return a(args, 0) + scope.rng.nextDouble() * (a(args, 1) - a(args, 0));
                return scope.rng.nextDouble();
            }
            case "random_int": {
                int lo = (int) a(args, 0);
                int hi = (int) a(args, 1);
                if (hi <= lo) return lo;
                return lo + scope.rng.nextInt(hi - lo + 1);
            }
            case "die_roll":  return scope.rng.nextInt((int) a(args, 0)) + 1;
            case "sign":      return Math.signum(a(args, 0));
            case "mod":       return a(args, 1) == 0 ? 0 : a(args, 0) % a(args, 1);
            case "pi":        return Math.PI;
            case "tau":       return Math.PI * 2.0;
            case "to_rad":    return Math.toRadians(a(args, 0));
            case "to_deg":    return Math.toDegrees(a(args, 0));
            case "hermite_blend": return a(args, 0); // close enough
            default:          return 0.0;
        }
    }

    private static double a(List<Double> args, int i) {
        return i < args.size() ? args.get(i) : 0.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double invLerp(double a, double b, double v) {
        if (a == b) return 0;
        return (v - a) / (b - a);
    }
}
