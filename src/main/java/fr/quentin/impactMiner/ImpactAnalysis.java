package fr.quentin.impactMiner;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;

import spoon.MavenLauncher;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtAnnotationFieldAccess;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtArrayWrite;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtSynchronized;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtVisitor;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.LexicalScope;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;

public class ImpactAnalysis {
    // preEval -> deps -> validation
    static private Logger logger = Logger.getLogger(ImpactAnalysis.class.getName());

    private final Integer maxChainLength;

    private final Resolver resolver;

    public final AugmentedAST<MavenLauncher> augmented;

    public ImpactAnalysis(final AugmentedAST<MavenLauncher> _ast) {
        this(_ast, 10);
    }

    public ImpactAnalysis(final AugmentedAST<MavenLauncher> augmentedAst, final int maxChainLength) {
        this.augmented = augmentedAst;
        this.maxChainLength = maxChainLength;

        this.resolver = new Resolver(augmented.launcher.getModel().getAllTypes());
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

    public Set<SourcePosition> getImpactedInvocations(final String string, final int i, final int j) {
        return null;
    }

    public Set<SourcePosition> getImpactingDeclarations(final String string, final int i, final int j) {
        return null;
    }

    public <T> List<ImpactChain> getImpactedTests2(final Collection<ImmutablePair<Object, Position>> col)
            throws IOException {
        return getImpactedTests2(col, true);
    }

    public <T> List<ImpactChain> getImpactedTests3(final Collection<ImmutablePair<Object, CtElement>> col,
            final boolean onTests) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final ImmutablePair<Object, CtElement> x : col) {
            final CtElement ele = x.right;
            final Object impactingThing = x.left;
            // List<CtElement> tmp = this.launcher.getModel().getElements(filter);
            final SourcePosition pos = ele.getPosition();
            assert pos.isValidPosition() : pos;
            final ImpactElement tmp2 = new ImpactElement(ele);
            tmp2.addEvolution(impactingThing,
                    new Position(pos.getFile().getAbsolutePath(), pos.getSourceStart(), pos.getSourceEnd()));
            chains.add(new ImpactChain(tmp2));
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, onTests);
    }

    public <T> List<ImpactChain> getImpactedTests4(final Collection<ImmutablePair<Object, Object>> col,
            final boolean onTests) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final ImmutablePair<Object, Object> x : col) {
            final Object impactingThing = x.left;
            CtElement element = null;
            Position position = null;
            if (x.right instanceof CtElement) {
                element = (CtElement) x.right;
                // List<CtElement> tmp = this.launcher.getModel().getElements(filter);
                final SourcePosition pos = element.getPosition();
                assert pos.isValidPosition() : pos;
                position = new Position(pos.getFile().getAbsolutePath(), pos.getSourceStart(), pos.getSourceEnd());
            } else if (x.right instanceof Position) {
                position = (Position) x.right;
                final CtType<?> tmp0 = this.augmented.topsByFileName.get(position.getFilePath());
                if (tmp0 == null) {
                    continue;
                }
                element = Utils.matchExact((CtElement) tmp0, position.getStart(), position.getEnd() - 1);
            }
            assert element != null : position;
            assert position != null : element;
            final ImpactElement tmp2 = new ImpactElement(element);
            tmp2.addEvolution(impactingThing, position);
            chains.add(new ImpactChain(tmp2));
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, onTests);
    }

    public <T> List<ImpactChain> getImpactedTests2(final Collection<ImmutablePair<Object, Position>> col,
            final boolean onTests) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final ImmutablePair<Object, Position> x : col) {
            final Object impactingThing = x.left;
            final Position pos = x.right;
            final CtType<?> tmp0 = this.augmented.topsByFileName.get(pos.getFilePath());
            if (tmp0 == null) {
                continue;
            }
            CtElement element = Utils.matchExact((CtElement) tmp0, pos.getStart(), pos.getEnd() - 1);
            assert element != null : element;
            final ImpactElement tmp2 = new ImpactElement(element);
            tmp2.addEvolution(impactingThing, pos);
            chains.add(new ImpactChain(tmp2));
        }
        Logger.getLogger("getImpactedTests").info(Integer.toString(chains.size()));
        return exploreAST2(chains, onTests);
    }

    public <T> List<ImpactChain> getImpactedTests(final Collection<Evolution<T>> x) throws IOException {
        final Set<ImpactChain> chains = new HashSet<>();
        for (final Evolution<T> impactingThing : x) {
            for (final Position pos : impactingThing.getPreEvolutionPositions()) {
                final List<CtElement> tmp = this.augmented.launcher.getModel().getElements(new FilterEvolvedElements(
                        Paths.get(this.augmented.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
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
                final List<CtElement> tmp = this.augmented.launcher.getModel().getElements(new FilterEvolvedElements(
                        Paths.get(this.augmented.rootFolder.toAbsolutePath().toString(), pos.getFilePath()).toString(),
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

    /**
     * Explorer is used to follow dependencies: - types dependencies, from
     * declaration to references - data dependencies, flow dependencies, read after
     * write dependency - do not consider other data dep - overshoot on : -
     * parameter usage of non primitive type parameter, consider all of them as
     * potential writes - all dynamic resolutions - type hierachy
     */
    private class Explorer {
        public final List<ImpactChain> finishedChains = new ArrayList<ImpactChain>();
        // dependency chain redundant with other chains, longer than the one that is
        // continued
        public final List<ImpactChain> redundantChains = new ArrayList<ImpactChain>();
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

        public <T> void followUsage(final ImpactChain current, final CtExecutable<T> current_elem, final Integer weight)
                throws IOException {
            Set<CtAbstractInvocation<T>> z = resolver.references(current_elem);
            for (final CtAbstractInvocation<T> invocation : z) {
                if (!invocation.getPosition().isValidPosition())
                    continue;
                final ImpactChain extended = current.extend(new ImpactElement(invocation), "call");
                putIfNotRedundant(extended, weight - 1);
            }
            CtExecutable<?> override = resolver.override(current_elem);
            if (override != null && override.getPosition().isValidPosition()) {
                final ImpactChain extended = current.extend(new ImpactElement(override), "override");
                putIfNotRedundant(extended, weight - 1);

            }
            Set<CtExecutable<?>> overrides = resolver.overrides(current_elem);
            for (CtExecutable<?> overri : overrides) {
                if (overri.getPosition().isValidPosition()) {
                    final ImpactChain extended = current.extend(new ImpactElement(overri), "overrided by");
                    putIfNotRedundant(extended, weight - 1);

                }

            }
        }

        /**
         * followArguments consider all passed arguments that are of non primitive types
         * as potential writes
         * 
         * @param <T>
         * @param current
         * @param current_elem
         * @param weight
         * @throws IOException
         */
        public <T> void followValueArguments(final ImpactChain current, final CtAbstractInvocation<T> current_elem,
                final Integer weight) throws IOException {
            final List<CtExpression<?>> arguments = ((CtAbstractInvocation<?>) current_elem).getArguments();
            int current_argument_index = 0;
            for (final CtExpression<?> argument : arguments) {
                if (!argument.getPosition().isValidPosition())
                    continue;
                final Map<String, Object> more = new HashMap<>();
                if (!argument.getType().isPrimitive()) {
                    more.put("index", current_argument_index);
                    followExprFromArgument(current, argument, more, weight);
                }
                current_argument_index++;
            }
        }

        private void followExprFromArgument(final ImpactChain current, final CtExpression<?> expr,
                final Map<String, Object> more, final Integer weight) throws IOException {
            if (expr instanceof CtAbstractInvocation) {
                final ImpactChain extended = current.extend(new ImpactElement(expr), "argument access", more);
                // putIfNotRedundant(extended, weight - 1); // I don't think it's a good idea to
                // put it in the queue as is :/
                // TODO Maybe follow target calling this method ?
                int current_argument_index = 0;
                for (CtExpression<?> arg : ((CtAbstractInvocation<?>) expr).getArguments()) {
                    final Map<String, Object> more2 = new HashMap<>();
                    more2.put("index", current_argument_index);
                    followExprFromArgument(extended, arg, more2, weight - 1);
                    current_argument_index++;
                }
                // } else if (expr instanceof CtSuperAccess) { // should allow more precise
                // things
            } else if (expr instanceof CtSuperAccess) {
                throw new RuntimeException("do not handle " + expr.getClass().getCanonicalName());
            } else if (expr instanceof CtThisAccess) {
                CtThisAccess<?> thisAccess = (CtThisAccess<?>) expr;
                CtElement parent = thisAccess.getParent();
                if (parent instanceof CtFieldAccess) {
                    CtType<?> top = parent.getParent(CtType.class).getTopLevelType();
                    Set<CtType<?>> implems = resolver.referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                    CtVariable<?> fieldDecl = resolver.reference((CtFieldAccess<?>) parent);
                    final ImpactChain extended = current.extend(new ImpactElement(fieldDecl), "argument access", more);
                    Integer weight2 = weight - 1;
                    for (CtVariableAccess<?> access : resolver.references((CtField<?>) fieldDecl)) {
                        if (top.hasParent(access)) {
                            final ImpactChain extended2 = extended.extend(new ImpactElement(access), "value");
                            putIfNotRedundant(extended2, weight2 - 1);
                        }
                        if (implems != null) {
                            for (CtType<?> implem : implems) {
                                if (implem.hasParent(access)) {
                                    final ImpactChain extended2 = extended.extend(new ImpactElement(access), "value");
                                    putIfNotRedundant(extended2, weight2 - 1);
                                }
                            }
                        } else {
                            Set<CtType<?>> aaa = resolver
                                    .referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                        }
                    }
                } else {
                    assert false : parent;
                }
            } else if (expr instanceof CtTypeAccess && expr.getParent() instanceof CtFieldAccess
                    && (((CtFieldAccess<?>) expr.getParent()).getVariable().getSimpleName().equals("class")
                            || ((CtFieldAccess<?>) expr.getParent()).getVariable().getDeclaration().isFinal())) {
                return;
            } else if (expr instanceof CtTypeAccess && expr.getParent(CtType.class)
                    .equals(((CtTypeAccess<?>) expr).getAccessedType().getTypeDeclaration())) {
                CtTypeAccess<?> thisAccess = (CtTypeAccess<?>) expr;
                CtElement parent = thisAccess.getParent();
                if (parent instanceof CtFieldAccess) {
                    CtType<?> top = parent.getParent(CtType.class).getTopLevelType();
                    Set<CtType<?>> implems = resolver.referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                    CtVariable<?> fieldDecl = resolver.reference((CtFieldAccess<?>) parent);
                    final ImpactChain extended = current.extend(new ImpactElement(fieldDecl), "argument access", more);
                    Integer weight2 = weight - 1;
                    for (CtVariableAccess<?> access : resolver.references((CtField<?>) fieldDecl)) {
                        if (top.hasParent(access)) {
                            final ImpactChain extended2 = extended.extend(new ImpactElement(access), "value");
                            putIfNotRedundant(extended2, weight2 - 1);
                        }
                        if (implems != null) {
                            for (CtType<?> implem : implems) {
                                if (implem.hasParent(access)) {
                                    final ImpactChain extended2 = extended.extend(new ImpactElement(access), "value");
                                    putIfNotRedundant(extended2, weight2 - 1);
                                }
                            }
                        } else {
                            Set<CtType<?>> aaa = resolver
                                    .referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                        }
                    }
                } else {
                    assert false : parent;
                }
            } else if (expr instanceof CtTargetedExpression) { // big approx
                CtTargetedExpression<?, ?> targeted = (CtTargetedExpression<?, ?>) expr;
                followExprFromArgument(current, targeted.getTarget(), more, weight - 1);
            } else if (expr instanceof CtVariableAccess) {
                CtVariable<?> x = resolver.reference((CtVariableAccess<?>) expr);
                if (x!=null) {
                    final ImpactChain extended = current.extend(new ImpactElement(x), "argument access", more);
                    putIfNotRedundant(extended, weight - 1);
                }
            } else if (expr instanceof CtAssignment) {
                CtAssignment<?, ?> assign = (CtAssignment<?, ?>) expr;
                followExprFromArgument(current, assign.getAssigned(), more, weight - 1);
            } else if (expr instanceof CtConditional) {
                CtConditional<?> cond = (CtConditional<?>) expr;
                followExprFromArgument(current, cond.getThenExpression(), more, weight - 1);
                followExprFromArgument(current, cond.getElseExpression(), more, weight - 1);
            } else if (expr instanceof CtBinaryOperator) {
                CtBinaryOperator<?> op = (CtBinaryOperator<?>) expr;
                followExprFromArgument(current, op.getLeftHandOperand(), more, weight - 1);
                followExprFromArgument(current, op.getRightHandOperand(), more, weight - 1);
            } else if (expr instanceof CtUnaryOperator) {
                CtUnaryOperator<?> op = (CtUnaryOperator<?>) expr;
                followExprFromArgument(current, op.getOperand(), more, weight - 1);
            } else if (expr instanceof CtLiteral) {
                return;
            } else {
                assert false : expr;
            }
        }

        private Integer putIfNotRedundant(final ImpactChain chain, final Integer weight) {
            final Integer existing_weight = alreadyMarchedChains.get(chain);
            if (existing_weight == null) {
                processedChains.add(chain);
                alreadyMarchedChains.put(chain, weight);
            } else if (weight > existing_weight) {
                redundantChains.add(chain);
            } else {
                redundantChains.add(chain);
            }
            return weight;
        }

        private CtVariableAccess<?> unnest(CtExpression<?> expr) {
            if (expr instanceof CtThisAccess) {
                return (CtVariableAccess<?>) expr;
            } else if (expr instanceof CtSuperAccess) {
                return (CtVariableAccess<?>) expr;
            } else if (expr instanceof CtVariableAccess) {
                return (CtVariableAccess<?>) expr;
            } else if (expr instanceof CtTargetedExpression) {
                return unnest(((CtTargetedExpression<?, ?>) expr).getTarget());
            } else {
                throw new RuntimeException("unnest do not handle: " + expr.getClass());
            }
        }
        // private CtVariable<?> solveTargeted(CtTargetedExpression<?,?> expr) {
        // if (expr instanceof CtVariableAccess) {
        // return resolver.reference((CtVariableAccess<?>) expr);
        // } else if (expr instanceof CtTargetedExpression) {
        // return solveTargeted(((CtTargetedExpression<?, ?>) expr).getTarget());
        // } else {
        // throw new RuntimeException("solveLHS do not handle: " + expr.getClass());
        // }
        // }

        public void followValue(final ImpactChain current, final CtExpression<?> current_elem, final Integer weight)
                throws IOException {
            try {
                // if (current_elem instanceof CtTargetedExpression) {
                // CtVariable<?> variable = solveTargeted((CtTargetedExpression<?,?>)
                // current_elem);
                // if (variable != null) {
                // final ImpactChain extended = current.extend(new ImpactElement(variable),
                // "read");
                // putIfNotRedundant(extended, weight - 1);
                // }
                // }
                CtElement parent = current_elem.getParent();
                CtRole roleInParent = current_elem.getRoleInParent();
                // roleInParent.getSuperRole();
                if (parent == null) {
                } else if (parent instanceof CtReturn && roleInParent.equals(CtRole.EXPRESSION)) {
                    try {
                        CtExecutable parent2 = parent.getParent(CtExecutable.class);
                        final ImpactChain extended = current.extend(new ImpactElement(parent2), "return");
                        putIfNotRedundant(extended, weight - 1);
                    } catch (final ParentNotInitializedException e) {
                        logger.log(Level.WARNING, "parentNotInitializedException", e);
                    }
                } else if (parent instanceof CtVariable && roleInParent.equals(CtRole.DEFAULT_EXPRESSION)) {
                    final ImpactChain extended = current.extend(new ImpactElement(parent), "value");
                    putIfNotRedundant(extended, weight - 1);
                } else if (parent instanceof CtAbstractInvocation && roleInParent.equals(CtRole.ARGUMENT)) {
                    final ImpactChain extended = current.extend(new ImpactElement(parent), "argument");
                    putIfNotRedundant(extended, weight - 1);
                } else if (parent instanceof CtAbstractInvocation && roleInParent.equals(CtRole.TARGET)) {
                    final ImpactChain extended = current.extend(new ImpactElement(parent), "value");
                    putIfNotRedundant(extended, weight - 1);
                } else if (parent instanceof CtAssignment && roleInParent.equals(CtRole.ASSIGNMENT)) {
                    CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
                    CtExpression<?> assignedExpr = assignment.getAssigned();
                    if (assignedExpr != null) {
                        CtVariableAccess<?> unnested = unnest(assignedExpr);
                        if (unnested instanceof CtThisAccess) {
                            CtThisAccess<?> thisAccess = (CtThisAccess<?>) unnested;
                            CtElement thisAccessParent = thisAccess.getParent();
                            if (thisAccessParent instanceof CtFieldAccess) {
                                CtType<?> top = thisAccessParent.getParent(CtType.class).getTopLevelType();
                                Set<CtType<?>> implems = resolver
                                        .referencesSuperClass((CtType<?>) thisAccessParent.getParent(CtType.class));
                                CtVariable<?> fieldDecl = resolver.reference((CtFieldAccess<?>) thisAccessParent);
                                final ImpactChain extended = current.extend(new ImpactElement(fieldDecl), "value");
                                Integer weight2 = putIfNotRedundant(extended, weight - 1);
                                for (CtVariableAccess<?> access : resolver.references((CtField<?>) fieldDecl)) {
                                    if (top.hasParent(access)) {
                                        final ImpactChain extended2 = extended.extend(new ImpactElement(access),
                                                "value");
                                        putIfNotRedundant(extended2, weight2 - 1);
                                    }
                                    for (CtType<?> implem : implems) {
                                        if (implem.hasParent(access)) {
                                            final ImpactChain extended2 = extended.extend(new ImpactElement(access),
                                                    "value");
                                            putIfNotRedundant(extended2, weight2 - 1);
                                        }
                                    }
                                }
                            } else {
                                throw new RuntimeException("" + thisAccessParent);
                            }
                        } else if (unnested instanceof CtSuperAccess) {
                            assert false : unnested;
                        } else if (unnested instanceof CtTypeAccess) {
                            assert false : unnested;
                        } else {
                            final ImpactChain extended = current.extend(new ImpactElement(resolver.reference(unnested)),
                                    "value");
                            putIfNotRedundant(extended, weight - 1);
                        }
                    }
                    // } else if (parent instanceof CtBinaryOperator) {
                    // } else if (parent instanceof CtUnaryOperator) {
                    // } else if (parent instanceof CtNewArray) {
                    // } else if (parent instanceof CtTypeAccess) {
                    // } else if (parent instanceof CtLiteral) {
                    // } else if (parent instanceof CtConditional) {
                    // } else if (parent instanceof CtVariableAccess) {
                } else if (parent instanceof CtArrayAccess && roleInParent.equals(CtRole.EXPRESSION)) {
                    return;
                } else if (parent instanceof CtConditional && roleInParent.equals(CtRole.CONDITION)) {
                    return;
                } else if (parent instanceof CtBlock) {
                    return;
                } else if (parent instanceof CtIf && roleInParent.equals(CtRole.CONDITION)) {
                    final ImpactChain extended = current.extend(new ImpactElement(((CtIf) parent)), "condition");
                    putIfNotRedundant(extended, weight - 10);
                    return;
                } else if (parent instanceof CtIf && roleInParent.equals(CtRole.THEN)) {
                    final ImpactChain extended = current.extend(new ImpactElement(((CtIf) parent)), "then");
                    putIfNotRedundant(extended, weight - 10);
                    return;
                } else if (parent instanceof CtIf && roleInParent.equals(CtRole.ELSE)) {
                    final ImpactChain extended = current.extend(new ImpactElement(((CtIf) parent)), "else");
                    putIfNotRedundant(extended, weight - 10);
                    return;
                } else if (parent instanceof CtLoop && roleInParent.equals(CtRole.BODY)) {
                    final ImpactChain extended = current.extend(new ImpactElement(((CtLoop) parent)), "body");
                    putIfNotRedundant(extended, weight - 10);
                    return;
                } else if (parent instanceof CtForEach && roleInParent.equals(CtRole.EXPRESSION)) {
                    final ImpactChain extended = current.extend(new ImpactElement(((CtForEach) parent).getVariable()),
                            "value");
                    putIfNotRedundant(extended, weight - 1);
                    return;
                } else if (parent instanceof CtThrow) { // TODO implement something to follow value in catch clause
                    final ImpactChain extended = current.extend(new ImpactElement(((CtThrow) parent)), "throw");
                    putIfNotRedundant(extended, weight - 1);
                    return;
                } else if (parent instanceof CtBinaryOperator) {
                } else if (parent instanceof CtFieldRead) { // TODO confirm doing nothing
                } else if (parent instanceof CtUnaryOperator) {
                    if (((CtUnaryOperator<?>) parent).getKind().equals(UnaryOperatorKind.COMPL)) {
                    } else if (((CtUnaryOperator<?>) parent).getKind().equals(UnaryOperatorKind.NEG)) {
                    } else if (((CtUnaryOperator<?>) parent).getKind().equals(UnaryOperatorKind.NOT)) {
                    } else if (((CtUnaryOperator<?>) parent).getKind().equals(UnaryOperatorKind.POS)) {
                    } else {
                        assert false : parent;
                    }
                } else {
                    logger.log(Level.WARNING,
                            "followValue case not handled: " + parent.getClass() + " " + roleInParent.name());
                    assert false : parent;
                }

                if (parent instanceof CtExpression) {
                    followValue(current, (CtExpression<?>) parent, weight - 1);
                } else {
                    CtElement parentAlt = current_elem.getParent(CtExecutable.class);
                    if (parentAlt != null) {
                        final ImpactChain extended = current.extend(new ImpactElement((parentAlt)),
                                "possible side effect in context");
                        putIfNotRedundant(extended, weight - 10);
                    }
                }
            } catch (final ParentNotInitializedException e) {
                logger.log(Level.WARNING, "parentNotInitializedException", e);
            }
        }

        private Filter<CtCodeElement> scopeFilter = new Filter<CtCodeElement>() {

            @Override
            public boolean matches(CtCodeElement element) {
                return element instanceof CtBlock || element instanceof CtCase || element instanceof CtCatch
                        || element instanceof CtSwitch || element instanceof CtIf || element instanceof CtSynchronized
                        || element instanceof CtLoop || element instanceof CtTry || element instanceof CtType
                        || element instanceof CtExecutable;
            }

        };

        public void expandToScopeOtherwiseExecutableOtherwiseType(final ImpactChain current,
                final CtElement current_elem, final Integer weight) throws IOException {
            try {
                final CtCodeElement parentScopeBlock = current_elem.getParent(scopeFilter);
                if (parentScopeBlock != null) {
                    if (parentScopeBlock instanceof CtType) {
                        final ImpactChain extended = current.extend(new ImpactElement(parentScopeBlock),
                                "expand");
                        putIfNotRedundant(extended, weight - 1);
                    } else if (parentScopeBlock instanceof CtExecutable) {
                        final ImpactChain extended = current.extend(new ImpactElement(parentScopeBlock),
                                "expand");
                        putIfNotRedundant(extended, weight - 1);
                    } else if (parentScopeBlock instanceof CtBlock) {
                        final CtElement parentExecutable = parentScopeBlock.getParent();
                        if (parentExecutable instanceof CtExecutable) {
                            final ImpactChain extended = current.extend(new ImpactElement(parentExecutable),
                                    "expand");
                            putIfNotRedundant(extended, weight - 1);
                        } else {
                            final ImpactChain extended = current.extend(new ImpactElement(parentScopeBlock),
                                    "expand");
                            putIfNotRedundant(extended, weight - 1);
                        }
                    } else {
                        final ImpactChain extended = current.extend(new ImpactElement(parentScopeBlock),
                                "expand");
                        putIfNotRedundant(extended, weight - 1);
                    }
                } else {
                    finishChain(current);
                }
            } catch (final ParentNotInitializedException e) {
                logger.info("ParentNotInitializedException");
            }
            // TODO expand to type (class for example as a modifier of a class or an extends
            // or an implements)

            // TODO how should @override or abstract be handled (virtual call)
        }

        public void followUses(final ImpactChain current, final CtType<?> current_elem, final Integer weight)
                throws IOException {
            Set<CtType<?>> superClasses = resolver.referencesSuperClass(current_elem);
            Set<CtType<?>> superInterfaces = resolver.referencesSuperInterface(current_elem);
            Set<CtTypedElement<?>> allUses = resolver.references(current_elem);

            for (final CtType<?> type : superClasses) {
                if (!type.getPosition().isValidPosition())
                    continue;
                final ImpactChain extended = current.extend(new ImpactElement(type), "class");
                putIfNotRedundant(extended, weight - 1);
            }
            for (final CtType<?> type : superInterfaces) {
                if (!type.getPosition().isValidPosition())
                    continue;
                final ImpactChain extended = current.extend(new ImpactElement(type), "interface");
                putIfNotRedundant(extended, weight - 1);

            }
            for (final CtTypedElement<?> type : allUses) {
                if (!type.getPosition().isValidPosition())
                    continue;
                final ImpactChain extended = current.extend(new ImpactElement(type), "type");
                putIfNotRedundant(extended, weight - 1);
            }
        }

        // public void followTypes(final ImpactChain current, final CtTypedElement
        // current_elem, final Integer weight)
        // throws IOException {
        // final CtTypeReference<?> typeRef = current_elem.getType();
        // final Object z = current_elem.getMetadata("type");
        // if (z == null) {
        // } else if (z instanceof Collection) {
        // final Collection<?> a = (Collection<?>) z;
        // for (final Object b : a) {
        // final CtType<?> type = (CtType<?>) b;
        // if (!type.getPosition().isValidPosition())
        // continue;
        // final ImpactChain extended = current.extend(new ImpactElement(type), "type");
        // final Integer fromAlreadyMarched2 = alreadyMarchedChains.get(extended);
        // if (fromAlreadyMarched2 == null || weight - 1 > fromAlreadyMarched2) {
        // processedChains.add(extended);
        // alreadyMarchedChains.put(extended, weight - 1);
        // } else {
        // logger.info("Ignoring redundant impact path");
        // }
        // }
        // } else {
        // logger.info("type not handled " + z.getClass().getName());
        // }
        // }

        // public <T> void followReads(final ImpactChain current, final CtExpression<T>
        // current_elem, final Integer weight)
        // throws IOException {
        // // final CtQuery q = launcher.getFactory().createQuery();
        // // final Set<CtVariableRead> s = new
        // // HashSet(q.setInput(current_elem).list(CtVariableRead.class));
        // // for (final CtVariableRead x : s) {
        // // if (!x.getPosition().isValidPosition())
        // // continue;
        // // final ImpactChain extended = current.extend(new ImpactElement(x), "read");
        // // final Integer existing_weight = alreadyMarchedChains.get(extended);
        // // if (existing_weight == null || weight - 1 > existing_weight) {
        // // processedChains.add(extended);
        // // alreadyMarchedChains.put(extended, weight - 1);
        // // } else {
        // // logger.info("Ignoring redundant impact path");
        // // }
        // // }
        // final List<CtVariableRead<?>> s = current_elem.getElements(new
        // TypeFilter<>(CtVariableRead.class));
        // for (final CtVariableRead<?> x : s) {
        // CtVariable<?> v = resolver.reference(x);
        // if (!v.getPosition().isValidPosition())
        // continue;
        // final ImpactChain extended = current.extend(new ImpactElement(x), "read");
        // final Integer existing_weight = alreadyMarchedChains.get(extended);
        // if (existing_weight == null || weight - 1 > existing_weight) {
        // processedChains.add(extended);
        // alreadyMarchedChains.put(extended, weight - 1);
        // } else {
        // logger.info("Ignoring redundant impact path");
        // }
        // }
        // // final List<CtAbstractInvocation<?>> invs = current_elem.getElements(new
        // TypeFilter<>(CtAbstractInvocation.class));
        // // for (final CtAbstractInvocation<?> x : invs) {
        // // CtExecutable<?> v = resolver.references(x);
        // // if (!v.getPosition().isValidPosition())
        // // continue;
        // // final ImpactChain extended = current.extend(new ImpactElement(x), "read");
        // // final Integer existing_weight = alreadyMarchedChains.get(extended);
        // // if (existing_weight == null || weight - 1 > existing_weight) {
        // // processedChains.add(extended);
        // // alreadyMarchedChains.put(extended, weight - 1);
        // // } else {
        // // logger.info("Ignoring redundant impact path");
        // // }
        // // }
        // }

        // public void followWrite(final ImpactChain current, final CtStatement
        // current_elem, final Integer weight)
        // throws IOException {
        // }

        public <T> void followVariableValueAndUses(final ImpactChain current, final CtVariable<T> current_elem,
                final Integer weight) throws IOException {
            // if (current_elem instanceof CtLocalVariable) {
            // final CtLocalVariable<?> tmp = (CtLocalVariable<?>) current_elem;
            // final CtExecutable<?> enclosingScope = tmp.getParent(CtExecutable.class);
            // final CtQuery q = launcher.getFactory().createQuery();Write
            // if (!x.getPosition().isValidPosition())
            // continue;
            // final ImpactChain extended = current.extend(new ImpactElement(x), "write");
            // final Integer existing_weight = alreadyMarchedChains.get(extended);
            // if (existing_weight == null || weight - 1 > existing_weight) {
            // processedChains.add(extended);
            // alreadyMarchedChains.put(extended, weight - 1);
            // } else {
            // logger.info("Ignoring redundant impact path");
            // }
            // }
            // } else if (current_elem instanceof CtAssignment) {
            // // CtAssignment<?, ?> tmp = (CtAssignment<?, ?>) current_elem;
            // } else {

            // }

            // SENS DU FLOW DES DONNEES
            Set<CtVariableAccess<T>> s = resolver.references(current_elem);
            for (final CtVariableAccess<T> x : s) {
                if (!x.getPosition().isValidPosition())
                    continue;
                if (x instanceof CtVariableRead) {
                    final ImpactChain extended = current.extend(new ImpactElement(x), "read");
                    putIfNotRedundant(extended, weight - 1);
                }
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

            if (weight <= 0) {
                explorer.finishChain(current);
            } else if (current_elem instanceof CtExecutable) {
                if (isTest((CtExecutable<?>) current_elem)) {
                    explorer.finishedChains.add(current);
                } else {
                    explorer.followUsage(current, (CtExecutable<?>) current_elem, weight);
                }
            } else if (current_elem instanceof CtExpression) {
                explorer.followValue(current, (CtExpression<?>) current_elem, weight);
                if (current_elem instanceof CtAbstractInvocation) {
                    // argument possible writes
                    explorer.followValueArguments(current, (CtAbstractInvocation<?>) current_elem, weight);
                    // current type
                }
                // explorer.followTypes(current, (CtExpression<?>) current_elem, weight); //
                // current type
                // } else if (current_elem instanceof CtStatement) {
            } else if (current_elem instanceof CtVariable) {
                explorer.followVariableValueAndUses(current, (CtVariable<?>) current_elem, weight);
                // explorer.followTypes(current, (CtLocalVariable) current_elem, weight); //
                // current type
                // } else if (current_elem instanceof CtAssignment) { // is an expression
                // explorer.followReads(current, (CtAssignment) current_elem, weight);
                // // explorer.followTypes(current, (CtAssignment) current_elem, weight); //
                // // current type
                // } else if (current_elem instanceof CtReturn) {
                // // explorer.followTypes(current, ((CtReturn)
                // // current_elem).getReturnedExpression(), weight); // current
                // // type
                // explorer.followReads(current, (CtExpression<?>) ((CtReturn)
                // current_elem).getReturnedExpression(),
                // weight);
                // explorer.expand3(current, current_elem, weight); // returns
            } else if (current_elem instanceof CtType) {
                explorer.followUses(current, (CtType<?>) current_elem, weight);
            } else {
                explorer.expandToScopeOtherwiseExecutableOtherwiseType(current, current_elem, weight);
            }
        }
        List<ImpactChain> res = new ArrayList<>();
        res.addAll(explorer.redundantChains); // at this point can't filter the one that do not impact tests
        res.addAll(explorer.finishedChains);
        return res;
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
