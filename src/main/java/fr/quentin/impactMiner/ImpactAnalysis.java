package fr.quentin.impactMiner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collector;

import javax.management.Query;

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
import spoon.processing.FactoryAccessor;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtCatchVariable;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.VariableAccessFilter;
import spoon.support.reflect.declaration.CtClassImpl;
import spoon.support.reflect.declaration.CtMethodImpl;
import spoon.support.util.internal.MapUtils;

public class ImpactAnalysis {

    static private Logger logger = Logger.getLogger(ImpactAnalysis.class.getName());

    private final Integer maxChainLength;
    private final SpoonAPI launcher;
    // private Map<>
    private final Set<Path> testDirs;
    private final Set<Path> srcDirs;
    private final Set<CtType<?>> testThings = new HashSet<>();
    private final Set<CtType<?>> srcThings = new HashSet<>();
    private Path rootFolder;
    private final Map<String, CtType<?>> typesIndexByFileName = new HashMap<>();
    // private Map<CtType<?>, Set<CtMethod<?>>> testEntries;
    private final List<CtExecutableReference<?>> allMethodsReferences;

    private final Resolver resolver;

    public ImpactAnalysis(final MavenLauncher launcher) {
        this(launcher, 10);
    }

    public ImpactAnalysis(final MavenLauncher launcher, final int maxChainLength) {
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

        final Collection<CtType<?>> allTypes = launcher.getModel().getAllTypes();
        for (final CtType<?> type : allTypes) {
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
                final CtType<?> aaaaa = typesIndexByFileName.put(relativized.toString(), type);
                try {
                    if (aaaaa != null && type.getPosition().getFile().toString()
                            .equals(aaaaa.getPosition().getFile().toString())) {
                        if (aaaaa.getPosition().getSourceStart() <= type.getPosition().getSourceStart()
                                && aaaaa.getPosition().getSourceEnd() >= type.getPosition().getSourceEnd()) {
                            typesIndexByFileName.put(relativized.toString(), aaaaa);
                        }
                    }
                } finally {

                }
            }
        }

        this.allMethodsReferences = new ArrayList<>();
        for (final CtMethod<?> m : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
            this.allMethodsReferences.add(m.getReference());
        }
        this.launcher.getModel().getRootPackage().accept(new ImpactPreprossessor(allMethodsReferences));

        this.resolver = new Resolver(allTypes);
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

    public <T> List<ImpactChain> getImpactedTests2(final Collection<ImmutablePair<Object, Position>> col)
            throws IOException {
        return getImpactedTests2(col, true);
    }

    public <T> List<ImpactChain> getImpactedTests2(final Collection<ImmutablePair<Object, Position>> col,
            final boolean onTests) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final ImmutablePair<Object, Position> x : col) {
            final Position pos = x.right;
            final Object impactingThing = x.left;
            final FilterEvolvedElements filter = new FilterEvolvedElements(
                    Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                    pos.getStart(), pos.getEnd());
            final CtType<?> tmp0 = this.typesIndexByFileName.get(pos.getFilePath());
            if (tmp0 == null) {
                continue;
            }
            final List<CtElement> tmp = tmp0.getElements(filter);
            // List<CtElement> tmp = this.launcher.getModel().getElements(filter);
            for (final CtElement element : tmp) {
                final ImpactElement tmp2 = new ImpactElement(element);
                tmp2.addEvolution(impactingThing, pos);
                chains.add(new ImpactChain(tmp2));
            }
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, onTests);
    }

    public <T> List<ImpactChain> getImpactedTests(final Collection<Evolution<T>> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final Evolution<T> impactingThing : x) {
            for (final Position pos : impactingThing.getPreEvolutionPositions()) {
                final List<CtElement> tmp = this.launcher.getModel()
                        .getElements(new FilterEvolvedElements(
                                Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                                pos.getStart(), pos.getEnd()));
                for (final CtElement element : tmp) {
                    final ImpactElement tmp2 = new ImpactElement(element);
                    tmp2.addEvolution((Evolution<Object>) impactingThing, pos);
                    chains.add(new ImpactChain(tmp2));
                }
            }
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, true);
    }

    public <T> List<ImpactChain> getImpactedTestsPostEvolution(final Collection<Evolution<T>> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final Evolution<T> impactingThing : x) {
            for (final Position pos : impactingThing.getPostEvolutionPositions()) {
                final List<CtElement> tmp = this.launcher.getModel()
                        .getElements(new FilterEvolvedElements(
                                Paths.get(this.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
                                pos.getStart(), pos.getEnd()));
                for (final CtElement element : tmp) {
                    final ImpactElement tmp2 = new ImpactElement(element);
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

        private final String file;
        private final int start;
        private final int end;

        public FilterEvolvedElements(final Position position) {
            this.file = position.getFilePath();
            this.start = position.getStart();
            this.end = position.getEnd();
        }

        public FilterEvolvedElements(final String file, final int start, final int end) {
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
                } catch (final Exception e) {
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

    static final String METADATA_KEY_REVERSE = "reversed-" + UUID.randomUUID();
    static final String METADATA_KEY_EXTENDS = "extends-" + UUID.randomUUID();
    static final String METADATA_KEY_IMPLEMENTS = "implements-" + UUID.randomUUID();
    static final String METADATA_KEY_INVS_COUNT = "invsCount-" + UUID.randomUUID();
    static final String METADATA_KEY_REVERSE_COUNT = "accessCount-" + UUID.randomUUID();

    final static class Uses<T> {
        private final Set<T> values = new HashSet<>();
        private final Class<T> type;

        Uses(final Class<T> class1) {
            this.type = class1;
        }

        public Class<T> getType() {
            return type;
        }

        public void add(final T value) {
            assert type.isInstance(value);
            values.add(value);
        }

        public Set<T> getValues() {
            return Collections.unmodifiableSet(values);
        }
    }

    /**
     * Explorer
     */
    private class Explorer {
        public final List<ImpactChain> finishedChains = new ArrayList<ImpactChain>();
        public final ConcurrentLinkedQueue<ImpactChain> processedChains = new ConcurrentLinkedQueue<>();
        public final HashMap<ImpactChain, Integer> alreadyMarchedChains = new HashMap<ImpactChain, Integer>();
        private final boolean getOnTests;

        public Explorer(final Set<ImpactChain> impactChains, final boolean getOnTests) {
            processedChains.addAll(impactChains);
            this.getOnTests = getOnTests;
        }

        public void finishChain(final ImpactChain chain) {
            if (getOnTests) {
                logger.info("Ignoring redundant impact path");
            } else {
                finishedChains.add(chain);
            }
        }

        public <T> void followAbstractInvocations(final ImpactChain current, final CtExecutable<T> current_elem,
                final Integer weight) throws IOException {
            final Object z = current_elem.getMetadata("call");
            if (z instanceof Collection) {
                final Collection<?> a = (Collection<?>) z;
                for (final Object b : a) {
                    if (b instanceof CtAbstractInvocation) {
                        final CtAbstractInvocation<?> invocation = (CtAbstractInvocation<?>) b;
                        if (!invocation.getPosition().isValidPosition())
                            continue;
                        final ImpactChain extended = current.extend(new ImpactElement(invocation), "call");
                        final Integer fromAlreadyMarched2 = alreadyMarchedChains.get(extended);
                        if (fromAlreadyMarched2 == null || weight - 10 > fromAlreadyMarched2) {
                            processedChains.add(extended);
                            alreadyMarchedChains.put(extended, weight - 10);
                        } else {
                            Logger.getLogger("ImpactLogger").info("Ignoring redundant impact path");
                        }
                    } else {
                        Logger.getLogger("ImpactLogger").info("call MD content not handled " + z.getClass().getName());
                    }
                }
            } else if (z == null) {
                // Logger.getLogger("ImpactLogger").info("no Meta Data found");
            } else {
                Logger.getLogger("ImpactLogger").info("call MD not handled " + z.getClass().getName());
            }
        }

        public <T> void followParameters(final ImpactChain current, final CtAbstractInvocation<T> current_elem,
                final Integer weight) throws IOException {
            final List<CtExpression<?>> arguments = ((CtAbstractInvocation<?>) current_elem).getArguments();
            int current_argument_index = 0;
            for (final CtExpression<?> argument : arguments) {
                if (!argument.getPosition().isValidPosition())
                    continue;
                final Map<String, Object> more = new HashMap<>();
                more.put("index", current_argument_index);
                final ImpactChain extended = current.extend(new ImpactElement(argument), "argument", more);
                final Integer existing_weight = alreadyMarchedChains.get(extended);
                if (existing_weight == null || weight - 1 > existing_weight) {
                    processedChains.add(extended);
                    alreadyMarchedChains.put(extended, weight - 1);
                } else {
                    logger.info("Ignoring redundant impact path");
                }
                current_argument_index++;
            }
        }

        public <T> void expand2(final ImpactChain current, final CtAbstractInvocation<T> current_elem,
                final Integer weight) throws IOException {
            try {
                // expand to assignment
                final CtAssignment<?, ?> parentAssignment = current_elem.getParent(CtAssignment.class);
                CtExpression<?> assignedExpr = null;
                if (parentAssignment != null) {
                    assignedExpr = parentAssignment.getAssigned();
                }
                if (assignedExpr != null && assignedExpr instanceof CtVariableWrite) {
                    final ImpactChain extended = current.extend(new ImpactElement(assignedExpr), "assignment");
                    alreadyMarchedChains.put(extended, weight - 1);
                    processedChains.add(extended);
                } else if (assignedExpr != null && assignedExpr instanceof CtArrayAccess) {
                    final ImpactChain extended = current.extend(new ImpactElement(assignedExpr), "assignment");
                    alreadyMarchedChains.put(extended, weight - 1);
                    processedChains.add(extended);
                } else {
                    // expand to variable
                    final CtVariable<?> parentVariable = current_elem.getParent(CtLocalVariable.class);
                    if (parentVariable != null) {
                        final ImpactChain extended0 = current.extend(new ImpactElement(parentVariable), "assignment");
                        final ImpactChain extended = extended0.extend(new ImpactElement(parentVariable), "write");
                        alreadyMarchedChains.put(extended, weight - 1);
                        processedChains.add(extended);
                    } else {
                        // expand to executable
                        final CtExecutable<?> parentExecutable = current_elem.getParent(CtExecutable.class);
                        if (parentExecutable != null) {
                            final ImpactChain extended = current.extend(new ImpactElement(parentExecutable),
                                    "expand to executable");
                            alreadyMarchedChains.put(extended, weight - 1);
                            processedChains.add(extended);
                        }
                    }
                }
            } catch (final ParentNotInitializedException e) {
                logger.info("ParentNotInitializedException");
            }
        }

        public void expand(final ImpactChain current, final CtElement current_elem, final Integer weight)
                throws IOException {
            try {

                // // expand to variable
                // final CtVariable<?> parentVariable =
                // current_elem.getParent(CtVariable.class);
                // final CtAssignment<?,?> aaaa = current_elem.getParent(CtAssignment.class);
                // CtExpression<?> bbbb = aaaa.getAssigned();
                // Set<CtVariableAccess<?>> a = findReferencings(bbbb); //
                // findVariableAccess(bbbb);

                // for (CtVariableAccess<?> b : a) {
                // Set<CtVariableAccess<?>> c = findReferencables(bbbb);
                // }
                // if (parentVariable != null) {
                // final ImpactChain extended = current.extend(new
                // ImpactElement(parentVariable),
                // "expand to variable");
                // explorer.alreadyMarchedChains.put(extended, weight + 0);
                // processedChains.add(extended);
                // } else {
                // // expand to executable
                final CtExecutable<?> parentExecutable = current_elem.getParent(CtExecutable.class);
                if (parentExecutable != null) {
                    final ImpactChain extended = current.extend(new ImpactElement(parentExecutable),
                            "expand to executable");
                    alreadyMarchedChains.put(extended, weight + 0);
                    processedChains.add(extended);
                }
                // }
            } catch (final ParentNotInitializedException e) {
                logger.info("ParentNotInitializedException");
            }
            // TODO expand to type (class for example as a modifier of a class or an extends
            // or an implements)

            // TODO how should @override or abstract be handled (virtual call)
        }

        public void followTypes(final ImpactChain current, final CtType current_elem, final Integer weight)
                throws IOException {
            final CtTypeReference<?> superClassRef = current_elem.getSuperclass();
            final Set<CtTypeReference<?>> supoerIntsRefs = current_elem.getSuperInterfaces();
        }

        public void followTypes(final ImpactChain current, final CtTypedElement current_elem, final Integer weight)
                throws IOException {
            final CtTypeReference<?> typeRef = current_elem.getType();
            final Object z = current_elem.getMetadata("type");
            if (z == null) {
            } else if (z instanceof Collection) {
                final Collection<?> a = (Collection<?>) z;
                for (final Object b : a) {
                    final CtType<?> type = (CtType<?>) b;
                    if (!type.getPosition().isValidPosition())
                        continue;
                    final ImpactChain extended = current.extend(new ImpactElement(type), "type");
                    final Integer fromAlreadyMarched2 = alreadyMarchedChains.get(extended);
                    if (fromAlreadyMarched2 == null || weight - 1 > fromAlreadyMarched2) {
                        processedChains.add(extended);
                        alreadyMarchedChains.put(extended, weight - 1);
                    } else {
                        logger.info("Ignoring redundant impact path");
                    }
                }
            } else {
                logger.info("type not handled " + z.getClass().getName());
            }
        }

        public <T> void followReads(final ImpactChain current, final CtExpression<T> current_elem, final Integer weight)
                throws IOException {
            final CtQuery q = launcher.getFactory().createQuery();
            final Set<CtVariableRead> s = new HashSet(q.setInput(current_elem).list(CtVariableRead.class));
            for (final CtVariableRead x : s) {
                if (!x.getPosition().isValidPosition())
                    continue;
                final ImpactChain extended = current.extend(new ImpactElement(x), "read");
                final Integer existing_weight = alreadyMarchedChains.get(extended);
                if (existing_weight == null || weight - 1 > existing_weight) {
                    processedChains.add(extended);
                    alreadyMarchedChains.put(extended, weight - 1);
                } else {
                    logger.info("Ignoring redundant impact path");
                }
            }
        }

        public void followWrite(final ImpactChain current, final CtStatement current_elem, final Integer weight)
                throws IOException {
            if (current_elem instanceof CtLocalVariable) {
                final CtLocalVariable<?> tmp = (CtLocalVariable<?>) current_elem;
                final CtExecutable<?> enclosingScope = tmp.getParent(CtExecutable.class);
                final CtQuery q = launcher.getFactory().createQuery();
                final Set<CtVariableWrite> s = new HashSet(q.setInput(enclosingScope).list(CtVariableWrite.class));
                for (final CtVariableWrite x : s) {
                    if (!x.getPosition().isValidPosition())
                        continue;
                    final ImpactChain extended = current.extend(new ImpactElement(x), "write");
                    final Integer existing_weight = alreadyMarchedChains.get(extended);
                    if (existing_weight == null || weight - 1 > existing_weight) {
                        processedChains.add(extended);
                        alreadyMarchedChains.put(extended, weight - 1);
                    } else {
                        logger.info("Ignoring redundant impact path");
                    }
                }
            } else if (current_elem instanceof CtAssignment) {
                // CtAssignment<?, ?> tmp = (CtAssignment<?, ?>) current_elem;
            } else {

            }
        }

        public void expand3(final ImpactChain current, final CtElement current_elem, final Integer weight)
                throws IOException {
            try {
                final CtExecutable<?> parentExecutable = current_elem.getParent(CtExecutable.class);
                if (parentExecutable != null) {
                    final ImpactChain extended = current.extend(new ImpactElement(parentExecutable), "return");
                    alreadyMarchedChains.put(extended, weight - 1);
                    processedChains.add(extended);
                }
            } catch (final ParentNotInitializedException e) {
                logger.info("ParentNotInitializedException");
            }
        }
    }

    private List<ImpactChain> exploreAST2(final Set<ImpactChain> impactChains, final boolean getOnTests)
            throws IOException {
        final Explorer explorer = new Explorer(impactChains, getOnTests);

        while (!explorer.processedChains.isEmpty()) {
            final ImpactChain current = explorer.processedChains.poll();
            final CtElement current_elem = current.getLast().getContent();
            final Integer weight = explorer.alreadyMarchedChains.getOrDefault(current, maxChainLength * 1);
            if (current_elem instanceof CtExecutable) {
                if (isTest((CtExecutable<?>) current_elem)) {
                    explorer.finishChain(current);
                } else {
                    if (weight <= 0) {
                        explorer.finishChain(current);
                        continue;
                    }
                    explorer.followAbstractInvocations(current, (CtExecutable<?>) current_elem, weight);
                    explorer.followTypes(current, (CtExecutable<?>) current_elem, weight);
                }
            } else if (current_elem instanceof CtAbstractInvocation) {
                explorer.followParameters(current, (CtAbstractInvocation<?>) current_elem, weight); // parameters deps
                explorer.expand2(current, (CtAbstractInvocation<?>) current_elem, weight); // returned value
                // explorer.followTypes(current, (CtAbstractInvocation<?>)current_elem, weight);
                // // current type
            } else if (current_elem instanceof CtExpression) {
                explorer.followReads(current, (CtExpression<?>) current_elem, weight);
                explorer.followTypes(current, (CtExpression<?>) current_elem, weight); // current type
                // } else if (current_elem instanceof CtStatement) {
            } else if (current_elem instanceof CtLocalVariable) {
                explorer.followWrite(current, (CtLocalVariable) current_elem, weight);
                explorer.followTypes(current, (CtLocalVariable) current_elem, weight); // current type
            } else if (current_elem instanceof CtAssignment) {
                explorer.followWrite(current, (CtAssignment) current_elem, weight);
                explorer.followTypes(current, (CtAssignment) current_elem, weight); // current type
            } else if (current_elem instanceof CtReturn) {
                explorer.followTypes(current, ((CtReturn) current_elem).getReturnedExpression(), weight); // current
                                                                                                          // type
                explorer.followReads(current, (CtExpression<?>) ((CtReturn) current_elem).getReturnedExpression(),
                        weight);
                explorer.expand3(current, current_elem, weight); // returns

                // if (weight <= 0) {
                // explorer.finishChain(current);
                // continue;
                // }
                // Object z = current_elem.getMetadata("variable");
                // if (z == null) {
                // } else if (z instanceof Collection) {
                // Collection<?> a = (Collection<?>) z;
                // for (Object b : a) {
                // CtVariable<?> variable = (CtVariable<?>) b;
                // if (!variable.getPosition().isValidPosition())
                // continue;
                // ImpactChain extended = current.extend(new ImpactElement(variable),
                // "variable");
                // Integer fromAlreadyMarched2 = explorer.alreadyMarchedChains.get(extended);
                // if (fromAlreadyMarched2 == null || weight - 1 > fromAlreadyMarched2) {
                // explorer.processedChains.add(extended);
                // explorer.alreadyMarchedChains.put(extended, weight - 1);
                // } else {
                // logger.info("Ignoring redundant impact path");
                // }
                // }
                // } else {
                // logger.info("type not handled " + z.getClass().getName());
                // }
            } else if (current_elem instanceof CtType) {
                if (weight <= 0) {
                    explorer.finishChain(current);
                    continue;
                }
                explorer.followTypes(current, (CtType) current_elem, weight);
            } else {
                explorer.expand(current, current_elem, weight);
            }
        }
        return explorer.finishedChains;
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
