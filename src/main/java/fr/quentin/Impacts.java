package fr.quentin;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtElement;

public class Impacts implements JsonSerializable<ImpactElement> {

    private class Relations implements JsonSerializable<ImpactElement> {

        private Set<ImpactElement> causes;
        private Set<ImpactElement> effects;
        private ImpactElement vertice;
        private int depth;

        public Relations(ImpactElement v) {
            this.vertice = v;
            this.causes = new HashSet<ImpactElement>();
            this.effects = new HashSet<ImpactElement>();
        }

        public Relations(ImpactElement v, int depth) {
            this(v);
            this.depth = depth;
        }

        public boolean addCause(ImpactElement x) {
            return causes.add(x);
        }

        public boolean addEffect(ImpactElement x) {
            return effects.add(x);
        }

        public Set<ImpactElement> getCauses() {
            return causes;
        }

        public Set<ImpactElement> getEffects() {
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
            for (ImpactElement b : causes) {
                JsonObject o = new JsonObject();
                a.add(o);
                o.add("vertice", f.apply(vertice.getContent()));
                o.addProperty("id", vertice.hashCode());
                o.add("cause", f.apply(b.getContent()));
            }
            return a;
        }
    }

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
            tests.add(si.getLast());
            roots.add(si.getRoot());
            addCause(si, null);
            addCauseBis(si);
        }
    }

    private void addCause(ImpactChain si, ImpactElement prevCurr) {
        Map<ImpactElement, Relations> dag = verticesPerRoots.get(si.getRoot());
        ImpactElement curr = si.getLast();

        Relations tmp = dag.get(curr); // hash == 1 from JDK8?
        if (tmp == null) {
            tmp = new Relations(curr, si.size());
            dag.put(curr, tmp);
            ImpactChain prev = si.getPrevious();
            if (prev != null) {
                tmp.addCause(prev.getLast());
                if (prevCurr != null)
                    tmp.addEffect(prevCurr);
                addCause(prev, curr);
            }
        } else {
            ImpactChain prev = si.getPrevious();
            if (prev != null) {
                tmp.addCause(prev.getLast());
                if (prevCurr != null)
                    tmp.addEffect(prevCurr);
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
            rel.addCause(y.getLast());
            if (tmp != null) {
                rel.addEffect(tmp);
            }
            tmp = x.getLast();
            dag.put(x.getLast(), rel);
            x = y;
        }
    }

    @Override
    public JsonElement toJson(ToJson f) {
        JsonObject a = new JsonObject();
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
                    // } else if (x instanceof Relations) {
                    // Relations y = (Relations) x;
                    // CtElement vert = (CtElement) y.getVertice().getContent();
                    // SourcePosition p = vert.getPosition();
                    // return new JsonPrimitive(p.hashCode());
                    // } else if (x instanceof SourcePositionHolder) {
                    // SourcePositionHolder y = (SourcePositionHolder) x;
                    // SourcePosition p = y.getPosition();
                    // return new JsonPrimitive(p.hashCode());
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
                    return o;
                } else if (x instanceof Relations) {
                    Relations y = (Relations) x;
                    JsonObject o = new JsonObject();
                    CtElement vert = (CtElement) y.getVertice().getContent();
                    // SourcePosition p = vert.getPosition();
                    o.addProperty("id", y.getVertice().hashCode());
                    o.add("value", f.apply(vert));
                    o.addProperty("depth", y.getDepth());
                    o.add("causes", h.apply(y.getCauses()));
                    o.add("effects", h.apply(y.getEffects()));
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

    // public Map<T, Map<T, Relations>> getVerticesPerRoots() {
    // return verticesPerRoots;
    // }

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