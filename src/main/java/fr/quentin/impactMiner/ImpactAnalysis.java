package fr.quentin.impactMiner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import org.apache.commons.lang3.tuple.ImmutablePair;

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
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.declaration.CtClassImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

public class ImpactAnalysis {

    private Integer maxChainLength;
    private SpoonAPI launcher;
    // private Map<>
    private Set<Path> testDirs;
    private Set<Path> srcDirs;
    private Set<CtType<?>> testThings = new HashSet<>();
    private Set<CtType<?>> srcThings = new HashSet<>();
    private Path rootFolder;
    private Map<String, CtType<?>> typesIndexByFileName = new HashMap<>();
    // private Map<CtType<?>, Set<CtMethod<?>>> testEntries;
    private List<CtExecutableReference<?>> allMethodsReferences;

    public ImpactAnalysis(final MavenLauncher launcher) {
        this(launcher, 10);
    }

    public ImpactAnalysis(final MavenLauncher launcher, int maxChainLength) {
        this.maxChainLength = maxChainLength;
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

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            Path relativized = null;
            try {
                relativized = rootFolder.relativize(type.getPosition().getFile().toPath().toRealPath());
            } catch (final IOException e) {
            }
            boolean isTest = false;
            if (relativized != null) {
                for (final Path file : testDirs) {
                    if (relativized.startsWith(file)) {
                        isTest = true;
                        break;
                    }
                }
            }
            if (isTest)
                this.testThings.add(type);

            boolean isNotTest = false;
            if (relativized != null) {
                for (final Path file : srcDirs) {
                    if (relativized.startsWith(file)) {
                        isNotTest = true;
                        break;
                    }
                }
            }
            if (isNotTest)
                this.srcThings.add(type);

            if (relativized != null) {
                CtType<?> aaaaa = typesIndexByFileName.put(relativized.toString(), type);
                try {

                    if(aaaaa!=null && type.getPosition().getFile().toString().equals(aaaaa.getPosition().getFile().toString())){
                        if (aaaaa.getPosition().getSourceStart()<=type.getPosition().getSourceStart() && aaaaa.getPosition().getSourceEnd()>=type.getPosition().getSourceEnd()) {
                            typesIndexByFileName.put(relativized.toString(), aaaaa);
                        }
                    }
                } finally {

                }
            }
        }

        // this.testEntries = new HashMap<>();
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

    // public void printTestEntries() {
    // for (final Map.Entry<CtType<?>, Set<CtMethod<?>>> x :
    // this.testEntries.entrySet()) {
    // System.out.println(x.getKey().getSimpleName());
    // for (final CtMethod<?> y : x.getValue()) {
    // System.out.println("\t" + y.getSignature());
    // }
    // }
    // }

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

    public void aaaaaa() {

        this.launcher.getModel().getAllTypes().iterator().next().getPosition();

    }

    public <T> List<ImpactChain> getImpactedTests2(Collection<ImmutablePair<Object, Position>> col) throws IOException {
        return getImpactedTests2(col, true);
    }

    public <T> List<ImpactChain> getImpactedTests2(Collection<ImmutablePair<Object, Position>> col, boolean onTests)
            throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (ImmutablePair<Object, Position> x : col) {
            Position pos = x.right;
            Object impactingThing = x.left;
            FilterEvolvedElements filter = new FilterEvolvedElements(
                    Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                    pos.getStart(), pos.getEnd());
            CtType<?> tmp0 = this.typesIndexByFileName.get(pos.getFilePath());
            if (tmp0==null) {
                continue;
            }
            List<CtElement> tmp = tmp0.getElements(filter);
            // List<CtElement> tmp = this.launcher.getModel().getElements(filter);
            for (CtElement element : tmp) {
                ImpactElement tmp2 = new ImpactElement(element);
                tmp2.addEvolution(impactingThing, pos);
                chains.add(new ImpactChain(tmp2));
            }
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, onTests);
    }

    public <T> List<ImpactChain> getImpactedTests(Collection<Evolution<T>> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (Evolution<T> impactingThing : x) {
            for (Position pos : impactingThing.getPreEvolutionPositions()) {
                List<CtElement> tmp = this.launcher.getModel()
                        .getElements(new FilterEvolvedElements(
                                Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                                pos.getStart(), pos.getEnd()));
                for (CtElement element : tmp) {
                    ImpactElement tmp2 = new ImpactElement(element);
                    tmp2.addEvolution((Evolution<Object>) impactingThing, pos);
                    chains.add(new ImpactChain(tmp2));
                }
            }
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, true);
    }

    public <T> List<ImpactChain> getImpactedTestsPostEvolution(Collection<Evolution<T>> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (Evolution<T> impactingThing : x) {
            for (Position pos : impactingThing.getPostEvolutionPositions()) {
                List<CtElement> tmp = this.launcher.getModel()
                        .getElements(new FilterEvolvedElements(
                                Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                                pos.getStart(), pos.getEnd()));
                for (CtElement element : tmp) {
                    ImpactElement tmp2 = new ImpactElement(element);
                    tmp2.addEvolution((Evolution<Object>) impactingThing, pos);
                    chains.add(new ImpactChain(tmp2));
                }
            }
        }
        Logger.getLogger("getImpactedTestsPostEvolution").info(Integer.toString(chains.size()));
        return exploreAST2(chains, true);
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

        public FilterEvolvedElements(String file, int start, int end) {
            this.file = file;
            this.start = start;
            this.end = end;
        }

        // public FilterEvolvedElements(ImpactElement p) {
        // this(p.getPosition());
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
                } catch (Exception e) {
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

    private List<ImpactChain> exploreAST2(final Set<ImpactChain> impactChains, boolean getOnTests) throws IOException {
        final List<ImpactChain> finishedChains = new ArrayList<ImpactChain>();
        final ConcurrentLinkedQueue<ImpactChain> processedChains = new ConcurrentLinkedQueue<>(impactChains);
        final HashMap<ImpactChain, Integer> alreadyMarchedChains = new HashMap<ImpactChain, Integer>();

        while (!processedChains.isEmpty()) {
            ImpactChain current = processedChains.poll();
            CtElement current_elem = current.getLast().getContent();
            Integer weight = alreadyMarchedChains.getOrDefault(current, maxChainLength * 1);
            if (current_elem instanceof CtExecutable) {
                if (isTest((CtExecutable<?>) current_elem)) {
                    finishedChains.add(current);
                } else {
                    if (weight < 0) {
                        if (getOnTests) {
                            Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
                        } else {
                            finishedChains.add(current);
                        }
                        continue;
                    }
                    Object z = current_elem.getMetadata("call");
                    if (z instanceof Collection) {
                        Collection<?> a = (Collection<?>) z;
                        for (Object b : a) {
                            if (b instanceof CtInvocation) {
                                CtInvocation<?> invocation = (CtInvocation<?>) b;
                                if (!invocation.getPosition().isValidPosition())
                                    continue;
                                ImpactChain extended = current.extend(new ImpactElement(invocation), "call");
                                Integer fromAlreadyMarched2 = alreadyMarchedChains.get(extended);
                                if (fromAlreadyMarched2 == null || weight - 1 > fromAlreadyMarched2) {
                                    processedChains.add(extended);
                                    alreadyMarchedChains.put(extended, weight - 1);
                                } else {
                                    Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
                                }
                            } else if (b instanceof CtConstructorCall) {
                                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) b;
                                if (!constructorCall.getPosition().isValidPosition())
                                    continue;
                                ImpactChain extended = current.extend(new ImpactElement(constructorCall), "call");
                                Integer fromAlreadyMarched2 = alreadyMarchedChains.get(extended);
                                if (fromAlreadyMarched2 == null || weight - 1 > fromAlreadyMarched2) {
                                    processedChains.add(extended);
                                    alreadyMarchedChains.put(extended, weight - 1);
                                } else {
                                    Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
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
                }
            } else {
                // expand to executable
                try {
                    final CtExecutable<?> parent = current_elem.getParent(CtExecutable.class);
                    if (parent != null) {
                        final ImpactChain extended = current.extend(new ImpactElement(parent), "expand to executable");
                        alreadyMarchedChains.put(extended, weight + 0);
                        processedChains.add(extended);
                    }
                } catch (ParentNotInitializedException e) {
                    Logger.getLogger("ImpactLogger").info("ParentNotInitializedException");
                }
                // TODO expand to type (class for example as a modifier of a class or an extends
                // or an implements)

                // TODO how should @override or abstract be handled (virtual call)
            }
        }
        return finishedChains;
    }

    private List<ImpactChain> exploreAST(final Set<ImpactChain> impactChains) throws IOException {
        final List<ImpactChain> finishedChains = new ArrayList<ImpactChain>();

        final ConcurrentLinkedQueue<ImpactChain> processedChains = new ConcurrentLinkedQueue<>(impactChains);

        // loops are not considered thanks to this set
        // ImpactChains are compared using the root and the head element, thus no chains
        // are lost
        // Only the cheapest (here in length) chain should be kept for a given root
        // cause and an impact
        // element
        final HashMap<ImpactChain, Integer> alreadyMarchedChains = new HashMap<ImpactChain, Integer>();

        while (!processedChains.isEmpty()) {
            ImpactChain current = processedChains.poll();
            CtElement current_elem = current.getLast().getContent();
            if (current_elem instanceof CtInvocation) {
                final CtInvocation<?> invocationElement = (CtInvocation<?>) current_elem;
                current_elem = invocationElement.getParent(CtExecutable.class); // AAA should be materialized by a
                                                                                // relation
                current = current.extend(new ImpactElement(current_elem), "expand to executable");
            } else if (current_elem instanceof CtConstructorCall) {
                final CtConstructorCall<?> constructionElement = (CtConstructorCall<?>) current_elem;
                current_elem = constructionElement.getParent(CtExecutable.class); // AAA should be materialized by a
                                                                                  // relation
                current = current.extend(new ImpactElement(current_elem), "expand to executable");
            }
            if (current_elem instanceof CtExecutable) {
                // final CtExecutable<?> executableElement = (CtExecutable<?>) elem;
                Integer fromAlreadyMarched = alreadyMarchedChains.get(current);
                if (fromAlreadyMarched != null) {
                    if (current.size() < fromAlreadyMarched) {
                        Logger.getLogger("ImpactLogger").info("Dropping a redundant impact path");
                        continue;
                    } else {
                        if (current.size() > maxChainLength) {
                            Logger.getLogger("ImpactLogger").info("Dropping a long impact");
                            continue;
                        }
                    }
                }
                alreadyMarchedChains.put(current, current.size());
                Object z = current_elem.getMetadata("call");
                // System.out.println("@@@@@ " + y.getLast().getSignature() + " " + s.size());
                if (z instanceof Collection) {
                    Collection<?> a = (Collection<?>) z;
                    for (Object b : a) {
                        // System.out.println("aaa");
                        if (b instanceof CtInvocation) {
                            // System.out.println("bbb");
                            CtInvocation<?> c = (CtInvocation<?>) b;
                            if (!c.getPosition().isValidPosition())
                                continue;
                            // System.out.println(c);
                            CtExecutable<?> p = c.getParent(CtExecutable.class); // AAA should be materialized by a
                                                                                 // relation
                            if (p != null) {
                                // System.out.println("ccc");
                                ImpactChain p2 = current.extend(new ImpactElement(c), "call");
                                Integer fromAlreadyMarched2 = alreadyMarchedChains.get(p2);
                                if (isTest(p)) {
                                    // System.out.println(p2.size());
                                    // if (fromAlreadyMarched2 == null || current.size() < fromAlreadyMarched2) {
                                    finishedChains.add(p2);
                                }
                                // } else {
                                if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
                                    alreadyMarchedChains.put(p2, p2.size());
                                    processedChains.add(p2);
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
                            if (!c.getPosition().isValidPosition())
                                continue;
                            CtElement p = c.getParent(CtConstructor.class); // AAA should be materialized by a relation
                            if (p != null) {
                                ImpactChain p2 = current.extend(new ImpactElement(c), "call");
                                Integer fromAlreadyMarched2 = alreadyMarchedChains.get(p2);
                                if (fromAlreadyMarched2 == null || current.size() > fromAlreadyMarched2) {
                                    alreadyMarchedChains.put(p2, p2.size());
                                    processedChains.add(p2);
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
            } else if (current_elem == null) {
                Logger.getLogger("ImpactLogger").warning("getLast returned null");
            } else {
                Logger.getLogger("ImpactLogger")
                        .info("Impact content not handled " + current_elem.getClass().getName());
            }

        }
        return finishedChains;
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
