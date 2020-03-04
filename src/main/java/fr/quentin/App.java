package fr.quentin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

public class App {
    public static void main(String[] args) throws IOException {
        // the second parameter can be APP_SOURCE / TEST_SOURCE / ALL_SOURCE
        // ../coming/pom.xml ../DummyProject/pom.xml ../gumtree-spoon-ast-diff/pom.xml
        // ../impact-miner/pom.xml ../server/pom.xml ../spoon/pom.xml
        // ../tools-changedistiller/pom.xml
        String studied = "/home/quentin/Documents/tools-changedistiller";
        if (args.length > 0) {
            studied = args[0];
        }
        MavenLauncher launcher = new MavenLauncher(studied, MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // list all packages of the model
        for (CtPackage p : model.getAllPackages()) {
            System.out.println("package: " + p.getQualifiedName());
        }
        // list all classes of the model
        for (CtType<?> s : model.getAllTypes()) {
            System.out.println("class: " + s.getQualifiedName());
        }
        // I want all posible links between calls and decls starting fron entry points
        // in tests

        // Queue<Integer> q = new LinkedList<>();
        // q.offer(1);
        // while (!q.isEmpty()) {
        // Integer i = q.poll();
        // System.out.println(i);
        // q.offer(i + 1);
        // }

        // Set<CtExecutableReference<?>> computedMethods = new HashSet<>();

        ImpactAnalysis l = new ImpactAnalysis(launcher);

        // launcher.getModel().getElements(MavenLauncher.)

        List<CtExecutableReference<?>> allExecutableReference = new ArrayList();
        for (CtMethod<?> m : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
            allExecutableReference.add(m.getReference());
        }
        for (CtConstructor<?> cstr : launcher.getModel().getElements(new TypeFilter<>(CtConstructor.class))) {
            allExecutableReference.add(cstr.getReference());
        }

        // launcher.getModel().getRootPackage().accept(new CtScanner() {
        // @Override
        // public <T> void visitCtMethod(CtMethod<T> a) {
        // super.visitCtMethod(a);
        // System.out.println("<<<<<<<<<<<<<<<<" +
        // a.getDeclaringType().getQualifiedName() + "." + a.getSignature() +
        // a.getMetadata("SomeMD"));
        // }
        // });
        boolean False = false;
        if (False) {
            launcher.getModel().getRootPackage().accept(new CtScanner() {
                // @Override
                // public <T> void visitCtExecutableReference(CtExecutableReference<T> a) {
                // super.visitCtExecutableReference(a);
                // Object x = a.getMetadata("call");
                // if (x != null) {
                // // System.out.println(">>>>>>>>1>>>>>>>>>>" + a.getClass());
                // // System.out.println(">>>>>>>>2>>>>>>>>>>" + x.getClass());
                // if (x instanceof Collection) {
                // Collection<?> y = (Collection<?>) x;
                // System.out.println(">>>>>>>>>>>>>>>>>>" +
                // a.getDeclaringType().getQualifiedName() + " " + y.size());
                // for (Object z : y) {
                // if (z instanceof CtInvocation) {
                // CtInvocation<?> zz = (CtInvocation<?>) z;
                // // System.out.println("\t" + zz);
                // } else if (z instanceof CtConstructorCall) {
                // CtConstructorCall<?> zz = (CtConstructorCall<?>) z;
                // // System.out.println("\t" + zz);
                // } else {
                // System.out.println("\t" + z.getClass());
                // }
                // }

                // }

                // }
                // }
                @Override
                public <T> void visitCtMethod(CtMethod<T> a) {
                    super.visitCtMethod(a);
                    Object x = a.getMetadata("call");
                    if (x != null) {
                        // System.out.println(">>>>>>>>1>>>>>>>>>>" + a.getClass());
                        // System.out.println(">>>>>>>>2>>>>>>>>>>" + x.getClass());
                        if (x instanceof Collection) {
                            Collection<?> y = (Collection<?>) x;
                            System.out.println(
                                    ">>>>>>>>>>>>>>>>>>" + a.getDeclaringType().getQualifiedName() + " " + y.size());
                            for (Object z : y) {
                                if (z instanceof CtInvocation) {
                                    CtInvocation<?> zz = (CtInvocation<?>) z;
                                    // if (zz.getParent(CtMethod.class) != null) {
                                    // System.out.println("\t Inv "+
                                    // zz.getParent(CtMethod.class).getDeclaringType().getQualifiedName()+" "+zz);
                                    // } else {
                                    System.out.println("\t Inv " + zz.getPosition());
                                    // }
                                } else if (z instanceof CtConstructorCall) {
                                    CtConstructorCall<?> zz = (CtConstructorCall<?>) z;
                                    System.out.println("\t Cons " + zz);
                                } else {
                                    System.out.println("\t" + z.getClass());
                                }
                            }

                        }

                    }
                }

                @Override
                public <T> void visitCtConstructor(CtConstructor<T> a) {
                    super.visitCtConstructor(a);
                    Object x = a.getMetadata("call");
                    if (x != null) {
                        // System.out.println(">>>>>>>>1>>>>>>>>>>" + a.getClass());
                        // System.out.println(">>>>>>>>2>>>>>>>>>>" + x.getClass());
                        if (x instanceof Collection) {
                            Collection<?> y = (Collection<?>) x;
                            System.out.println(
                                    ">>>>>>>>>>>>>>>>>>" + a.getDeclaringType().getQualifiedName() + " " + y.size());
                            for (Object z : y) {
                                if (z instanceof CtInvocation) {
                                    CtInvocation<?> zz = (CtInvocation<?>) z;
                                    System.out.println("\t Inv " + zz);
                                } else if (z instanceof CtConstructorCall) {
                                    CtConstructorCall<?> zz = (CtConstructorCall<?>) z;
                                    System.out.println("\t Cons " + zz);
                                } else {
                                    System.out.println("\t" + z.getClass());
                                }
                            }

                        }

                    }
                }
            });
        }

        List<Evolution> executableRefsAsPosition = new ArrayList<>();
        for (CtExecutableReference<?> aaaa : allExecutableReference.subList(50, 100)) {
            try {
                if (aaaa.getDeclaration().getPosition().isValidPosition()) {
                    executableRefsAsPosition.add(new Evolution1234(aaaa.getDeclaration()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<ImpactChain> imptst = l.getImpactedTests(executableRefsAsPosition);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Impacts x = new Impacts(imptst);
        System.out.println(gson.toJson(x.toJson(new ToJson() {
            public JsonElement apply(Object x) {
                if (x instanceof JsonSerializable) {
                    JsonSerializable<?> y = (JsonSerializable<?>) x;
                    return y.toJson(this);
                } else if (x instanceof CtMethod) {
                    CtMethod<?> y = (CtMethod<?>) x;
                    // return Integer.toString(x.hashCode());
                    return new JsonPrimitive(y.getDeclaringType().getQualifiedName() + y.getSignature());
                } else if (x instanceof CtConstructor) {
                    CtConstructor<?> y = (CtConstructor<?>) x;
                    // return Integer.toString(x.hashCode());
                    return new JsonPrimitive(y.getDeclaringType().getQualifiedName() + "." + y.getSignature());
                } else if (x instanceof CtExecutable) {
                    CtExecutable<?> y = (CtExecutable<?>) x;
                    // return Integer.toString(x.hashCode());
                    return new JsonPrimitive("anonymous block" + y.getSignature());
                } else if (x instanceof CtInvocation) {
                    CtInvocation<?> y = (CtInvocation<?>) x;
                    // return Integer.toString(x.hashCode());
                    JsonObject o = new JsonObject();
                    // o.addProperty("isInTest",ImpactAnalysis.isTest(y.getParent(CtExecutable.class)));
                    // o.addProperty("type",y.getClass().getName());
                    o.add("sig", apply(y.getExecutable().getDeclaration()));
                    o.add("Psig", apply(y.getParent(CtMethod.class)));
                    return o;
                } else if (x instanceof Collection) {
                    JsonArray a = new JsonArray();
                    for (Object b : (Collection<?>) x) {
                        a.add(apply(b));
                    }
                    return a;
                } else {
                    // return Integer.toString(x.hashCode());
                    return new JsonPrimitive(x.getClass().toString());
                }
            }
        })));

        System.out.println("Bye World!");
    }

    static class Evolution1234 implements Evolution {
        Set<Position> impacts = new HashSet<>();

        Evolution1234(SourcePositionHolder holder) throws IOException {
            SourcePosition p = holder.getPosition();
            this.impacts.add(new Position(p.getFile().getCanonicalPath(), p.getSourceStart(), p.getSourceEnd()));
        }

        @Override
        public Set<Position> getImpactingPositions() {
            return impacts;
        }
    }
}
// Set<SourcePosition> impinvo = l.getImpactedInvocations("",5,10);
// Set<SourcePosition> impdecl = l.getImpactingDeclarations("",5,10);

// System.out.println("0000000000000000000000");

// System.out.println(imptst);
// System.out.println();
// System.out.println(impinvo);
// System.out.println();
// System.out.println(impdecl);
// if (False) {
// launcher.getModel().getRootPackage().accept(new CtScanner() {
// @Override
// public <T> void visitCtInvocation(CtInvocation<T> invocation) {
// if (invocation.getExecutable().isConstructor()) {
// return;
// }
// for (CtExecutableReference<?> executable : allExecutableReference) {
// // if (computedMethods.contains(executable)) {
// // continue;
// // } else {
// // computedMethods.add(executable);
// // }
// if
// (invocation.getExecutable().getSimpleName().equals(executable.getSimpleName())
// && invocation.getExecutable().isOverriding(executable)) {

// // allMethods.add(executable);

// System.out.println("" +
// // invocation.getType().getDeclaration() +
// invocation.getPosition().getCompilationUnit().getDeclaredPackage() + "."
// + invocation.getPosition().getCompilationUnit().getFile().getName() + ":"
// + invocation.getPosition().getSourceStart() + ":"
// + invocation.getPosition().getSourceEnd() + "-->" +
// executable.getDeclaringType()
// + "." + executable
// // + ":" + executable.getType()
// );
// }
// }
// }
// });
// }
