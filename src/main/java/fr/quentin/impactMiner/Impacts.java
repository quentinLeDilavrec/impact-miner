package fr.quentin.impactMiner;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;

public class Impacts implements JsonSerializable {

    public static class Relations implements JsonSerializable {

        private Map<String, Set<ImpactElement>> causes;
        private Map<String, Set<ImpactElement>> effects;
        private ImpactElement vertice;
        private int depth;

        public Relations(ImpactElement v) {
            this.vertice = v;
            this.causes = new HashMap<>();
            this.effects = new HashMap<>();
        }

        public Relations(ImpactElement v, int depth) {
            this(v);
            this.depth = depth;
        }

        public boolean addCause(ImpactElement x, String type) {
            Set<ImpactElement> set = causes.get(type);
            if (set == null) {
                set = new HashSet<>();
                causes.put(type, set);
            }
            return set.add(x);
        }

        public boolean addEffect(ImpactElement x, String type) {
            Set<ImpactElement> set = effects.get(type);
            if (set == null) {
                set = new HashSet<>();
                effects.put(type, set);
            }
            return set.add(x);
        }

        public Map<String, Set<ImpactElement>> getCauses() {
            return causes;
        }

        public Map<String, Set<ImpactElement>> getEffects() {
            return effects;
        }

        public ImpactElement getVertice() {
            return vertice;
        }

        public int getDepth() {
            return depth;
        }

        @Override
        public JsonElement toJson(ToJson f) {
            JsonArray a = new JsonArray();
            for (Entry<String, Set<ImpactElement>> c : causes.entrySet()) {
                if (!c.getKey().equals("call"))
                    continue;
                for (ImpactElement b : c.getValue()) {
                    JsonObject o = new JsonObject();
                    a.add(o);
                    o.add("vertice", f.apply(vertice));
                    o.addProperty("id", vertice.hashCode());
                    o.add("cause", f.apply(b));
                }
            }
            return a;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((vertice == null) ? 0 : vertice.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Relations other = (Relations) obj;
            if (vertice == null) {
                if (other.vertice != null)
                    return false;
            } else if (!vertice.equals(other.vertice))
                return false;
            return true;
        }

    }

    // TODO try to remove ImpactElement wrapper
    private Map<ImpactElement, Map<ImpactElement, Relations>> verticesPerRoots;
    private Map<ImpactElement, Map<ImpactElement, Relations>> verticesPerTests;
    private Set<ImpactElement> tests;
    private Set<ImpactElement> roots;

    public Impacts(Collection<ImpactChain> x) {
        this.verticesPerRoots = new HashMap<>();
        this.verticesPerTests = new HashMap<>();
        this.tests = new HashSet<>();
        this.roots = new HashSet<>();
        for (ImpactChain si : x) {
            verticesPerRoots.putIfAbsent(si.getRoot(), new HashMap<>());
            verticesPerTests.putIfAbsent(si.getLast(), new HashMap<>());
            if (si.getLast().getContent() instanceof CtExecutable<?>
                    && ImpactAnalysis.isTest((CtExecutable<?>) si.getLast().getContent())) {
                tests.add(si.getLast());
            }
            roots.add(si.getRoot());
            addCause(si, null, null);
            addCauseBis(si);
        }
    }

    private void addCause(ImpactChain si, ImpactElement prevCurr, String prevType) {
        Map<ImpactElement, Relations> dag = verticesPerRoots.get(si.getRoot());
        ImpactElement curr = si.getLast();

        Relations tmp = dag.get(curr); // hash == 1 from JDK8?
        if (tmp == null) {
            tmp = new Relations(curr, si.size());
            dag.put(curr, tmp);
            ImpactChain prev = si.getPrevious();
            if (prevCurr != null)// && prevType!=null
                tmp.addEffect(prevCurr, prevType);
            if (prev != null) {
                tmp.addCause(prev.getLast(), si.getType());
                addCause(prev, curr, si.getType());
            }
        } else {
            ImpactChain prev = si.getPrevious();
            if (prevCurr != null)// && prevType!=null
                tmp.addEffect(prevCurr, prevType);
            if (prev != null) {
                tmp.addCause(prev.getLast(), si.getType());
            }
        }
    }

    private void addCauseBis(final ImpactChain si) {
        Map<ImpactElement, Relations> dag = verticesPerTests.get(si.getLast());

        ImpactChain x = si;
        ImpactElement tmp = null;
        while (x.getPrevious() != null) {
            ImpactChain y = x.getPrevious();

            Relations rel = new Relations(x.getLast(), x.size());
            rel.addCause(y.getLast(), si.getType());
            if (tmp != null) {
                rel.addEffect(tmp, si.getType());
            }
            tmp = x.getLast();
            dag.put(x.getLast(), rel);
            x = y;
        }
    }

    @Override
    public JsonElement toJson(ToJson f) {
        ToJson h = new ToJson() {
            public JsonElement apply(Object x) {
                if (x instanceof Collection) {
                    JsonArray a = new JsonArray();
                    for (Object b : (Collection<?>) x) {
                        a.add(apply(b));
                    }
                    return a;
                } else if (x instanceof ImpactElement) {
                    ImpactElement y = (ImpactElement) x;
                    return new JsonPrimitive(y.hashCode());
                } else {
                    return new JsonPrimitive(x.getClass().getCanonicalName());
                }
            }
        };
        ToJson g = new ToJson() {
            public JsonElement apply(Object x) {
                if (x instanceof Collection) {
                    JsonArray a = new JsonArray();
                    for (Object b : (Collection<?>) x) {
                        a.add(apply(b));
                    }
                    return a;
                } else if (x instanceof ImpactElement) {
                    ImpactElement y = (ImpactElement) x;
                    JsonObject o = new JsonObject();
                    o.addProperty("id", y.hashCode());
                    o.add("value", f.apply(y.getContent()));
                    for (Object e : y.getEvolutions()) {
                        o.add("evolution", f.apply(e));
                        break;
                    }
                    return o;
                } else if (x instanceof Relations) {
                    Relations y = (Relations) x;
                    JsonObject o = new JsonObject();
                    ImpactElement vert = y.getVertice();
                    o.addProperty("id", vert.hashCode());
                    o.add("value", f.apply(vert.getContent()));
                    for (Object e : vert.getEvolutions()) {
                        o.add("evolution", f.apply(e));
                        break;
                    }
                    o.addProperty("depth", y.getDepth());
                    // Set<ImpactElement> causes;
                    // Set<ImpactElement> effects;
                    // Set<ImpactElement> call_causes = y.getCauses().get("call");
                    // Set<ImpactElement> call_effects = y.getEffects().get("call");
                    // Set<ImpactElement> expand2exe_causes = y.getCauses().get("expand to
                    // executable");
                    // Set<ImpactElement> expand2exe_effects = y.getEffects().get("expand to
                    // executable");
                    // if ((call_causes != null || call_effects != null)
                    // && (expand2exe_causes == null && expand2exe_effects == null)) {
                    // o.addProperty("type", "call");
                    // causes = call_causes != null ? call_causes : new HashSet<>();
                    // effects = call_effects != null ? call_effects : new HashSet<>();
                    // } else if ((expand2exe_causes != null || expand2exe_effects != null)
                    // && (call_causes == null && call_effects == null)) {
                    // o.addProperty("type", "expand to executable");
                    // causes = expand2exe_causes != null ? expand2exe_causes : new HashSet<>();
                    // effects = expand2exe_effects != null ? expand2exe_effects : new HashSet<>();
                    // } else {
                    // o.addProperty("type", "unknown");
                    // causes = new HashSet<>();
                    // effects = new HashSet<>();
                    // }
                    // TODO label relations
                    Set<ImpactElement> causes = new HashSet<>();
                    Set<ImpactElement> effects = new HashSet<>();
                    Set<ImpactElement> call_causes = y.getCauses().getOrDefault("call", new HashSet<>());
                    Set<ImpactElement> call_effects = y.getEffects().getOrDefault("call", new HashSet<>());
                    Set<ImpactElement> expand2exe_causes = y.getCauses().getOrDefault("expand to executable",
                            new HashSet<>());
                    Set<ImpactElement> expand2exe_effects = y.getEffects().getOrDefault("expand to executable",
                            new HashSet<>());
                    causes.addAll(call_causes);
                    causes.addAll(expand2exe_causes);
                    effects.addAll(call_effects);
                    effects.addAll(expand2exe_effects);
                    o.add("causes", h.apply(causes));
                    o.add("effects", h.apply(effects));
                    return o;
                    // } else if (x instanceof SourcePositionHolder) {
                    // SourcePositionHolder y = (SourcePositionHolder) x;
                    // JsonObject o = new JsonObject();
                    // SourcePosition p = y.getPosition();
                    // o.addProperty("id", p.hashCode());
                    // o.add("value", f.apply(y));
                    // return o;
                } else {
                    return new JsonPrimitive(x.getClass().getCanonicalName());
                    // return f.apply(x);
                }
            }
        };
        JsonObject a = new JsonObject();
        JsonArray perRoots = new JsonArray();
        a.add("perRoot", perRoots);
        for (ImpactElement e : this.roots) {
            JsonObject o = new JsonObject();
            perRoots.add(o);
            Map<ImpactElement, Relations> curr = this.verticesPerRoots.get(e);
            o.add("vertices", g.apply(curr.values()));
            // o.add("edges", f.apply(curr.values()));
            o.addProperty("root", e.hashCode());
        }
        JsonArray perTests = new JsonArray();
        a.add("perTests", perTests);
        for (ImpactElement e : this.tests) {
            JsonObject o = new JsonObject();
            perTests.add(o);
            Map<ImpactElement, Relations> curr = this.verticesPerTests.get(e);
            o.add("vertices", g.apply(curr.values()));
            // o.add("edges", f.apply(curr.values()));
            o.addProperty("root", e.hashCode());
        }
        a.add("roots", h.apply(this.roots));
        a.add("tests", h.apply(this.tests));
        return a;
    }

    // private int root2hash(T e) {
    // if (e instanceof SourcePositionHolder) {
    // SourcePositionHolder y = (SourcePositionHolder) e;
    // SourcePosition p = y.getPosition();
    // return p.hashCode();
    // } else {
    // return 0;// e.hashCode() + e.getClass().hashCode();
    // }
    // }

    public Map<ImpactElement, Map<ImpactElement, Relations>> getVerticesPerRoots() {
        return verticesPerRoots;
    }

    public Set<ImpactElement> getTests() {
        return tests;
    }

    public Set<ImpactElement> getRoots() {
        return roots;
    }

    // protected void setVerticesPerRoots(Map<ImpactElement, Map<ImpactElement,
    // Relations>> verticesPerRoots) {
    // this.verticesPerRoots = verticesPerRoots;
    // }

    // protected void setTests(Set<ImpactElement> tests) {
    // this.tests = tests;
    // }

    // protected void setRoots(Set<ImpactElement> roots) {
    // this.roots = roots;
    // }

}