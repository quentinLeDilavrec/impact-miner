package fr.quentin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.logging.Logger;

import spoon.MavenLauncher;
import spoon.SpoonAPI;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.declaration.CtClassImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

public class ImpactAnalysis {

    private static final Integer MAX_CHAIN_LENGTH = 4;
    private SpoonAPI launcher;
    // private Map<>
    private Set<Path> testDirs;
    private Set<Path> srcDirs;
    private Set<CtType<?>> testThings;
    private Set<CtType<?>> srcThings;
    private Path rootFolder;
    private Map<CtType<?>, Set<CtMethod<?>>> testEntries;
    private List<CtExecutableReference<?>> allMethodsReferences;

    public ImpactAnalysis(final MavenLauncher launcher) {
        this.launcher = launcher;
        this.testDirs = new HashSet<>();
        this.srcDirs = new HashSet<>();
        try {
            this.rootFolder = launcher.getPomFile().getFileSystemParent().toPath().toRealPath();
            for (final File file : launcher.getPomFile().getTestDirectories()) {
                this.testDirs.add(rootFolder.relativize(file.toPath().toRealPath().toAbsolutePath()));
            }
            for (final File file : launcher.getPomFile().getSourceDirectories()) {
                this.srcDirs.add(rootFolder.relativize(file.toPath().toRealPath().toAbsolutePath()));
            }
        } catch (final IOException e1) {
        }
        this.testThings = launcher.getModel().getAllTypes().stream().filter((x) -> {
            for (final Path file : testDirs) {
                // System.out.println("+++++++++++++++++++");
                try {
                    // System.err.println(file.toRealPath().toAbsolutePath());
                    // System.err.println(rootFolder.relativize(file.toRealPath().toAbsolutePath()));
                    // System.err.println(x.getPosition().getFile().toPath().toRealPath());
                    // System.err.println(rootFolder.relativize(x.getPosition().getFile().toPath().toRealPath()));
                    // System.err.println(x.getSimpleName());
                    if (rootFolder.relativize(x.getPosition().getFile().toPath().toRealPath()).startsWith(file)) {
                        // System.out.println("===================");
                        return true;
                    }
                } catch (final IOException e) {
                }
            }
            return false;
        }).collect(Collectors.toSet());

        this.srcThings = launcher.getModel().getAllTypes().stream().filter((x) -> {
            for (final Path file : srcDirs) {
                try {
                    if (rootFolder.relativize(x.getPosition().getFile().toPath().toRealPath()).startsWith(file)) {
                        return true;
                    }
                } catch (final IOException e) {
                }
            }
            return false;
        }).collect(Collectors.toSet());

        // System.out.println("+++++++++++++++++++");
        // for (final CtType<?> x : testThings) {
        // System.out.println(x.getPosition().getFile().toPath());
        // }

        // System.out.println("********************");
        // for (final CtType<?> x : srcThings) {
        // System.out.println(x.getPosition().getFile().toPath());
        // }
        this.testEntries = new HashMap<>();
        this.allMethodsReferences = new ArrayList<>();
        for (CtMethod<?> m : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
            this.allMethodsReferences.add(m.getReference());
        }
        this.launcher.getModel().getRootPackage().accept(new ImpactPreprossessor(allMethodsReferences));
    }

    public static Boolean isTest(final CtExecutable<?> y) {
        for (final CtAnnotation<?> x : y.getAnnotations()) {
            // x.getAnnotationType().;
            if (Objects.equals(x.getAnnotationType().getQualifiedName(), "org.junit.Test")) {
                // System.out.println("\t\t" +
                // x.getAnnotationType().getQualifiedName()+"\n\t\t"+y.getSignature());
                return true;
            }
        }
        return false;
    }

    public void printTestEntries() {
        for (final Map.Entry<CtType<?>, Set<CtMethod<?>>> x : this.testEntries.entrySet()) {
            System.out.println(x.getKey().getSimpleName());
            for (final CtMethod<?> y : x.getValue()) {
                System.out.println("\t" + y.getSignature());
            }
        }
    }

    public Set<SourcePosition> getImpactedInvocations(final String string, final int i, final int j) {
        return null;
    }

    public Set<SourcePosition> getImpactingDeclarations(final String string, final int i, final int j) {
        return null;
    }

    // public <R> List<ImpactChain<CtElement,R>> getImpactedTests(final
    // SourcePosition position) {
    // File file = position.getFile();
    // if (file == null || !position.isValidPosition()) {
    // return null;
    // }
    // try {
    // return getImpactedTests(position.getFile().getCanonicalPath(),
    // position.getSourceStart(),
    // position.getSourceEnd());
    // } catch (IOException e) {
    // // TODO Auto-generated catch block
    // e.printStackTrace();
    // return null;
    // }
    // }

    public List<ImpactChain> getImpactedTests(Collection<Evolution> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (Evolution impactingThing : x) {
            for (Position pos : impactingThing.getImpactingPositions()) {
                List<CtElement> tmp = this.launcher.getModel().getElements(new FilterEvolvedElements(pos));
                for (CtElement element : tmp) {
                    ImpactElement tmp2 = new ImpactElement(element);
                    tmp2.getEvolutions().add(impactingThing);
                    chains.add(new ImpactChain(tmp2));
                }
            }
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST(chains);
    }

    // public <R> List<ImpactChain<CtElement,R>> getImpactedTests(String file, int
    // start, int end, R evolution) {
    // final List<CtElement> evolvedElements = this.launcher.getModel()
    // .getElements(new FilterEvolvedElements(file, start, end));
    // return exploreAST(evolvedElements);
    // }

    private class FilterEvolvedElements implements Filter<CtElement> {

        private String file;
        private int start;
        private int end;

        public FilterEvolvedElements(Position position) {
            this.file = position.getFilePath();
            this.start = position.getStart();
            this.end = position.getEnd();
        }

        // public FilterEvolvedElements(ImpactElement p) {
        //     this(p.getPosition());
        // }

        @Override
        public boolean matches(final CtElement element) {
            if (element instanceof CtExecutable<?>) {
                final SourcePosition p = element.getPosition();
                if (file == null || p.getFile() == null || !p.isValidPosition()) {
                    return false;
                }
                String c;
                try {
                    c = p.getFile().getCanonicalPath();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return false;

                }
                if (!Objects.equals(c, file)) {
                    return false;
                } else if (p.getSourceEnd() < start || end < p.getSourceStart()) {
                    return false;
                } else {
                    // included or overlaping
                    if (element instanceof CtMethod) {
                        return true;
                    } else if (element instanceof CtConstructor) {
                        return true;
                    } else if (element instanceof CtLambda) {
                        return true;
                    } else if (element instanceof CtAnonymousExecutable) {
                        return false; // TODO see if we can handle it
                    } else {
                        Logger.getLogger("FilterEvolvedElements.matches")
                                .warning(element.getClass() + " is not handled by the filter.");
                        return false;
                    }
                }
            } else {
                return false;
            }

        }
    }

    private List<ImpactChain> exploreAST(final Set<ImpactChain> impactChains) throws IOException {
        final ConcurrentLinkedQueue<ImpactChain> s = new ConcurrentLinkedQueue<>(impactChains);

        // loops are not considered
        final HashMap<ImpactChain, Integer> alreadyMarched = new HashMap<ImpactChain, Integer>();
        final List<ImpactChain> r = new ArrayList<ImpactChain>();
        // System.out.println("&&&&& " + x.size() + " " + s.size() + " " +
        // alreadyMarched.size());

        while (!s.isEmpty()) {
            // System.out.println(s.size());
            final ImpactChain current = s.poll();
            CtElement elem = current.getLast().getContent();
            if (elem instanceof CtInvocation) {
                final CtInvocation<?> invocationElement = (CtInvocation<?>) elem;
                elem = invocationElement.getParent(CtExecutable.class);
            } else if (elem instanceof CtConstructor) {
                final CtConstructor<?> constructionElement = (CtConstructor<?>) elem;
                elem = constructionElement.getParent(CtExecutable.class);
            }
            if (elem instanceof CtExecutable) {
                // final CtExecutable<?> executableElement = (CtExecutable<?>) elem;
                Integer fromAlreadyMarched = alreadyMarched.get(current);
                if (fromAlreadyMarched != null) {
                    if (current.size() < fromAlreadyMarched) {
                        Logger.getLogger("ImpactLogger").info("Dropping a redundant impact path");
                        continue;
                    } else {
                        if (current.size() > MAX_CHAIN_LENGTH) {
                            Logger.getLogger("ImpactLogger").info("Dropping a long impact");
                            continue;
                        }
                    }
                }
                alreadyMarched.put(current, current.size());
                Object z = elem.getMetadata("call");
                // System.out.println("@@@@@ " + y.getLast().getSignature() + " " + s.size());
                if (z instanceof Collection) {
                    Collection<?> a = (Collection<?>) z;
                    for (Object b : a) {
                        // System.out.println("aaa");
                        if (b instanceof CtInvocation) {
                            // System.out.println("bbb");
                            CtInvocation<?> c = (CtInvocation<?>) b;
                            // System.out.println(c);
                            CtExecutable<?> p = c.getParent(CtExecutable.class);
                            if (p != null) {
                                // System.out.println("ccc");
                                ImpactChain p2 = current.extend(new ImpactElement(c));
                                Integer fromAlreadyMarched2 = alreadyMarched.get(p2);
                                if (isTest(p)) {
                                    // System.out.println(p2.size());
                                    // if (fromAlreadyMarched2 == null || current.size() < fromAlreadyMarched2) {
                                    r.add(p2);
                                }
                                // } else {
                                if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
                                    s.add(p2);
                                } else {
                                    Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
                                    // System.out.println("???????????");
                                    // System.out.println(fromAlreadyMarched2);
                                    // System.out.println(current.size());
                                }
                                // }
                            }
                        } else if (b instanceof CtConstructorCall) {
                            CtConstructorCall<?> c = (CtConstructorCall<?>) b;
                            CtElement p = c.getParent(CtConstructor.class);
                            if (p != null) {
                                ImpactChain p2 = current.extend(new ImpactElement(c));
                                Integer fromAlreadyMarched2 = alreadyMarched.get(p2);
                                if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
                                    s.add(p2);
                                } else {
                                    Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
                                }
                            }
                        } else {
                            Logger.getLogger("ImpactLogger")
                                    .info("call MD content not handled " + z.getClass().getName());
                        }
                    }
                } else if (z == null) {
                    // Logger.getLogger("ImpactLogger").info("no Meta Data found");
                } else {
                    Logger.getLogger("ImpactLogger").info("call MD not handled " + z.getClass().getName());
                }
            } else if (elem == null) {
                Logger.getLogger("ImpactLogger").warning("getLast returned null");
            } else {
                Logger.getLogger("ImpactLogger").info("Impact content not handled " + elem.getClass().getName());
            }

        }
        return r;
    }

    // private List<ImpactChain<? extends CtElement>> exploreASTDecl(final
    // Collection<CtExecutable<?>> x) {
    // final ConcurrentLinkedQueue<ImpactChain<CtExecutable<?>>> s = new
    // ConcurrentLinkedQueue<>();
    // for (CtExecutable<?> y : x) {
    // s.add(new ImpactChain<CtExecutable<?>>(y));
    // }
    // // loop are not considered
    // final HashMap<ImpactChain<? extends CtElement>, Integer> alreadyMarched = new
    // HashMap<ImpactChain<? extends CtElement>, Integer>();
    // final List<ImpactChain<? extends CtElement>> r = new ArrayList<ImpactChain<?
    // extends CtElement>>();
    // // System.out.println("&&&&& " + x.size() + " " + s.size() + " " +
    // // alreadyMarched.size());

    // while (!s.isEmpty()) {
    // // System.out.println(s.size());
    // final ImpactChain<CtExecutable<?>> current = s.poll();
    // Integer fromAlreadyMarched = alreadyMarched.get(current);
    // if (fromAlreadyMarched != null) {
    // if (current.size() < fromAlreadyMarched) {
    // continue;
    // // System.out.println("22222222222222" + fromAlreadyMarched + " " +
    // // current.size());
    // } else {
    // if (current.size() > 4) {
    // continue;
    // }
    // }
    // }
    // alreadyMarched.put(current, current.size());
    // final Object z = current.getLast().getMetadata("call");
    // // System.out.println("@@@@@ " + y.getLast().getSignature() + " " +
    // s.size());
    // if (z instanceof Collection) {
    // final Collection<?> a = (Collection<?>) z;
    // for (final Object b : a) {
    // // System.out.println("aaa");
    // if (b instanceof CtInvocation) {
    // // System.out.println("bbb");
    // final CtInvocation<?> c = (CtInvocation<?>) b;
    // // System.out.println(c);
    // CtExecutable<?> p = c.getParent(CtExecutable.class);
    // if (p != null) {
    // // System.out.println("ccc");
    // ImpactChain<CtExecutable<?>> p2 = current.extend(p);
    // Integer fromAlreadyMarched2 = alreadyMarched.get(p);
    // if (isTest(p)) {
    // // System.out.println(p2.size());
    // // if (fromAlreadyMarched2 == null || current.size() < fromAlreadyMarched2) {
    // r.add(p2);
    // // }
    // } else {
    // if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
    // s.add(p2);
    // } else {
    // Logger.getLogger("exploreASTDecl").warn("redundant node");
    // }
    // }
    // }
    // } else if (b instanceof CtConstructorCall) {
    // final CtConstructorCall<?> c = (CtConstructorCall<?>) b;
    // final CtConstructor<?> p = c.getParent(CtConstructor.class);
    // Integer fromAlreadyMarched2 = alreadyMarched.get(p);
    // if (p != null) {
    // if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
    // s.add(current.extend(p));
    // }
    // }
    // } else {
    // }
    // }

    // }

    // }
    // return r;
    // }

}
