package fr.quentin.impactMiner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtReturn;
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
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.Filter;

/**
 * Explorer is used to follow dependencies: - types dependencies, from
 * declaration to references - data dependencies, flow dependencies, read after
 * write dependency - do not consider other data dep - overshoot on : -
 * parameter usage of non primitive type parameter, consider all of them as
 * potential writes - all dynamic resolutions - type hierachy
 */
public class Explorer {
    /**
     *
     */
    private final ImpactAnalysis impactAnalysis;
    // chains ending on a test declaration
    protected final List<ImpactChain> finishedChains = new ArrayList<>();
    // // dependency chain redundant with other chains, longer than the one that is continued
    // protected final List<ImpactChain> redundantChains = new ArrayList<>();
    // protected final ConcurrentLinkedQueue<ImpactChain> processedChains = new ConcurrentLinkedQueue<>();
    // protected final SortedMap<Integer,Set<ImpactChain>> callChains = new ConcurrentSkipListMap<>();
    // protected final Map<Integer, Set<ImpactChain>> callChainsAAA = new ConcurrentHashMap<>();
    protected final ConcurrentLinkedQueue<ImpactChain> callChains = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<ImpactChain> typeChains = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<ImpactChain> flowChains = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<ImpactChain> structChains = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<ImpactChain> otherChains = new ConcurrentLinkedQueue<>();
    protected final List<ImpactChain> abortedChains = new ArrayList<>();
    protected final HashMap<ImpactElement, ImpactElement> alreadyMarchedElement = new HashMap<>();
    // protected final HashMap<ImpactElement, Set<ImpactChain>> alreadyMarchedElementToChains = new HashMap<>(); // better to store that in each ele
    // protected final HashMap<ImpactChain, Integer> alreadyMarchedChains = new HashMap<>(); // idem
    private final boolean getOnTests;

    public List<ImpactChain> getFinishedChains() {
        return Collections.unmodifiableList(finishedChains);
    }

    // public List<ImpactChain> getRedundantChains() {
    //     return Collections.unmodifiableList(redundantChains);
    // }

    public List<ImpactChain> getAbortedChains() {
        return Collections.unmodifiableList(abortedChains);
    }

    private ImpactElement getImpactElement(CtElement next) {
        assert next != null;
        ImpactElement ie = new ImpactElement(next);
        alreadyMarchedElement.putIfAbsent(ie, ie);
        return alreadyMarchedElement.get(ie);
    }

    // /**
    //  * 
    //  * @param chain size()>0
    //  * @param possibility
    //  * @return non-Null
    //  */
    // public Collection<ImpactChain> destinationOracle(ImpactChain candidate) {
    //     if (candidate.size() > 100) {
    //         return abortedChains;
    //     }
    //     int best_weight_ele = (int) candidate.getLast().getMD(BEST_WEIGHT_CG, 0);
    //     int chain_weight = candidate.getMD(WEIGHT, 0);
    //     if (chain_weight <= best_weight_ele) {
    //         int nb = candidate.getLast().getMD(TESTS_REACHED, 0);
    //         if (nb > 0)
    //             setPrevsAsImpactingTests(candidate, nb);
    //         return redundantChains;
    //     }
    //     candidate.getLast().putMD(BEST_WEIGHT_CG, chain_weight);
    //     assert candidate.getType() != null;
    //     switch (candidate.getType().level) {
    //         case CALL_GRAPH:
    //             return callChains;
    //         case TYPE_GRAPH:
    //             return typeChains;
    //         case FLOW_GRAPH:
    //             return flowChains;
    //         case STRUCT_GRAPH:
    //             return structChains;
    //         case OTHER:
    //             return otherChains;
    //     }
    //     return abortedChains;
    // }

    // private ImpactChain proposeCommit(ImpactChain current) {
    //     destinationOracle(current).add(current);
    //     return current;
    // }

    public static CtExecutable<?> getHigherExecutable(final CtElement element) {
        CtExecutable<?> result;
        if (element == null) {
            return null;
        }
        if (element instanceof CtExecutable) {
            result = (CtExecutable<?>) element;
        } else {
            result = element.getParent(CtExecutable.class);
        }
        while (result != null) {
            CtExecutable<?> tmp = result.getParent(CtExecutable.class);
            if (tmp == null) {
                return result;
            }
            result = tmp;
        }
        return result;
    }

    /**
     * Can potentially be called concurently,
     * considering that if a thread add an element while other might skip it,
     * the local thread will always process it
     * @param level
     * @return
     */
    public ImpactType.Level process(ImpactType.Level level) {
        switch (level) {
            case CALL_GRAPH:
                return processCallChain();
            case TYPE_GRAPH:
                return processTypeChain();
            case FLOW_GRAPH:
                return processFlowChain();
            case STRUCT_GRAPH:
                return processStructChain();
            case OTHER:
                return processOtherChain();
        }
        return null;
    }

    public static Boolean isTest(final CtExecutable<?> y) {
        for (final CtAnnotation<?> x : y.getAnnotations()) {
            if (x.getAnnotationType().getQualifiedName().equals("org.junit.Test")) {
                return true;
            }
        }
        return false;
    }

    void setPrevsAsImpactingTests(ImpactChain chain) {
        ImpactChain current = chain;
        while (current != null && current.getMD(ImpactChain.TESTS_REACHED) == null) {
            current.putMD(ImpactChain.TESTS_REACHED, true);
            for (ImpactChain redundant : current.getMD(ImpactChain.REDUNDANT, new HashSet<ImpactChain>())) {
                if (redundant.getMD(ImpactChain.TESTS_REACHED) != null) {
                    setPrevsAsImpactingTests(redundant);
                }
            }
            current = current.getPrevious();
        }
    }

    // TODO !!!!!!!
    ImpactType.Level processCallChain() {
        ImpactChain current = callChains.poll();
        if (current == null) {
            return ImpactType.Level.TYPE_GRAPH;
        }
        if (current.size() > 100) {
            abortedChains.add(current);
        }
        int weight = current.getMD(ImpactChain.WEIGHT, 0);
        if (weight <= 0) {
            abortedChains.add(current);
        }
        ImpactChain best_old_chain = current.getLast().getMD(ImpactElement.BEST_CG);
        if (best_old_chain != null && weight <= best_old_chain.getMD(ImpactChain.WEIGHT, 0)) {
            if (current.getMD(ImpactChain.TESTS_REACHED) != null)
                setPrevsAsImpactingTests(current);
            HashSet<Object> redu = current.getMD(ImpactChain.REDUNDANT, new HashSet<>());
            redu.add(current);
            current.putMD(ImpactChain.REDUNDANT, redu);
            return ImpactType.Level.CALL_GRAPH;
        } else {
            current.getLast().putMD(ImpactElement.BEST_CG, current);
            if (best_old_chain != null) {
                Set<Object> aaaaaaa = current.getMD(ImpactChain.REDUNDANT, new HashSet<>());
                current.putMD(ImpactChain.REDUNDANT, aaaaaaa);
                aaaaaaa.add(best_old_chain);
            }
        }

        final CtElement current_elem = current.getLast().getContent();
        if (current_elem instanceof CtExecutable) {
            if (isTest((CtExecutable<?>) current_elem)) {
                setPrevsAsImpactingTests(current);
                finishedChains.add(current);
            }
            Set<ImpactChain> extendeds = followUsage(current, (CtExecutable<?>) current_elem, weight);
            callChains.addAll(extendeds);
        } else if (current_elem instanceof CtAbstractInvocation) {
            CtExecutable<?> parentExe = getHigherExecutable(current_elem);
            if (parentExe != null) {
                ImpactChain extended = current.extend(getImpactElement(parentExe), ImpactType.EXPAND,
                        weightedMore(weight));
                callChains.add(extended);
            }
            typeChains.add(current);
        } else {
            current.putMD("weight", current.getMD("weight", 1) * 2);
            typeChains.add(current);
        }
        return ImpactType.Level.CALL_GRAPH;
    }

    ImpactType.Level processTypeChain() {
        ImpactChain current = typeChains.poll();
        if (current == null) {
            return ImpactType.Level.FLOW_GRAPH;
        }
        if (current.size() > 100) {
            abortedChains.add(current);
        }
        int weight = current.getMD(ImpactChain.WEIGHT, 0);
        if (weight <= 0) {
            abortedChains.add(current);
        }

        Map<Integer, ImpactChain> best_old_chain_map = current.getLast().getMD(ImpactElement.BEST_TYPE);
        Integer curr_i = current.getMD("parameter index");
        if (best_old_chain_map != null) {
            ImpactChain best_old_chain = best_old_chain_map.get(curr_i);
            if (best_old_chain != null) {
                if (weight <= best_old_chain.getMD(ImpactChain.WEIGHT, 0)) {
                    if (current.getMD(ImpactChain.TESTS_REACHED) != null)
                        setPrevsAsImpactingTests(current);
                    HashSet<Object> redu = current.getMD(ImpactChain.REDUNDANT, new HashSet<>());
                    redu.add(current);
                    current.putMD(ImpactChain.REDUNDANT, redu);
                    return ImpactType.Level.TYPE_GRAPH;
                } else {
                    Set<Object> tmp = current.getMD(ImpactChain.REDUNDANT, new HashSet<>());
                    current.putMD(ImpactChain.REDUNDANT, tmp);
                    tmp.add(best_old_chain_map);
                    best_old_chain_map.put(curr_i, current);
                }
            } else {
                best_old_chain_map.put(curr_i, current);
            }

        } else {
            Map<Integer, ImpactChain> tmp = new HashMap<>();
            tmp.put(curr_i, current);
            current.getLast().putMD(ImpactElement.BEST_TYPE, tmp);
        }

        ImpactChain best_old_CG_chain = current.getLast().getMD(ImpactElement.BEST_CG);
        if (best_old_CG_chain != null) {
            Set<Object> tmp = current.getMD(ImpactChain.REDUNDANT, new HashSet<>());
            current.putMD(ImpactChain.REDUNDANT, tmp);
            tmp.add(best_old_CG_chain);
            current.getLast().putMD(ImpactElement.BEST_CG, null);
        }

        final CtElement current_elem = current.getLast().getContent();
        if (current_elem instanceof CtExecutable) {
            if (isTest((CtExecutable<?>) current_elem)) {
                finishedChains.add(current);
            } else {
                Integer i = current.getMD("parameter index");
                if (i != null) { //
                    ImpactElement ext_ele = getImpactElement(((CtExecutable<?>) current_elem).getParameters().get(i));
                    ImpactChain extended = current.extend(ext_ele, ImpactType.PARAMETER,
                            weightedMore(map("parameter index", i), weight));
                    typeChains.add(extended);
                }
                Set<ImpactChain> extendeds = followUsage(current, (CtExecutable<?>) current_elem, weight);
                typeChains.addAll(extendeds);
            }
        } else if (current_elem instanceof CtParameter) {
            CtExecutable<?> parentExe = ((CtParameter<?>) current_elem).getParent();
            Integer i = 0;
            for (CtParameter<?> param : parentExe.getParameters()) {
                if (param == current_elem) {
                    ImpactElement ext_ele = getImpactElement(parentExe);
                    Set<Integer> more = ext_ele.getMD("parameters", new HashSet<>());
                    more.add(i);
                    ext_ele.putMD("parameter", more);
                    ImpactChain extended = current.extend(ext_ele, ImpactType.PARAMETER,
                            weightedMore(map("parameter index", i), weight));
                    typeChains.add(extended);
                    break;
                }
                i++;
            }
        } else if (current_elem instanceof CtExpression) {
            flowChains.add(current);
            Set<ImpactChain> extendeds = followValue(current, (CtExpression<?>) current_elem, weight);
            typeChains.addAll(extendeds);
            if (current_elem instanceof CtAbstractInvocation) {
                Integer i = current.getMD("parameter index");
                if (i != null) { //
                    ImpactElement ext_ele = getImpactElement(
                            ((CtAbstractInvocation<?>) current_elem).getArguments().get(i));
                    ImpactChain extended = current.extend(ext_ele, ImpactType.PARAMETER,
                            weightedMore(map("parameter index", i), weight));
                    typeChains.add(extended);
                }
                // argument possible writes
                Set<ImpactChain> extendeds2 = followValueArguments(current, (CtAbstractInvocation<?>) current_elem,
                        weight);
                typeChains.addAll(extendeds2);
                // current type
            }
            // followTypes(current, (CtExpression<?>) current_elem); //
            // current type
            // } else if (current_elem instanceof CtStatement) {
        } else if (current_elem instanceof CtVariable) {
            // want to get direct checked usage
            // storing info to resolve type hierarchy things would be cool
            // !! An oracle would be good to tell if the targeted type thing changed,
            // as it might be a co-evolution thus the impact should be propagated further
            // otherwise it might terminate too early or go over too much unneeded things.
            Set<ImpactChain> extendeds = followVariableValueAndUses(current, (CtVariable<?>) current_elem, weight);
            typeChains.addAll(extendeds);
            // followTypes(current, (CtLocalVariable) current_elem); //
            // current type
            // } else if (current_elem instanceof CtAssignment) { // is an expression
            // followReads(current, (CtAssignment) current_elem);
            // // followTypes(current, (CtAssignment) current_elem); //
            // // current type
            // } else if (current_elem instanceof CtReturn) {
            // // followTypes(current, ((CtReturn)
            // // current_elem).getReturnedExpression()); // current
            // // type
            // followReads(current, (CtExpression<?>) ((CtReturn)
            // current_elem).getReturnedExpression(),
            //);
            // expand3(current, current_elem); // returns
        } else if (current_elem instanceof CtType) {
            Set<ImpactChain> extendeds = followUses(current, (CtType<?>) current_elem, weight);
            typeChains.addAll(extendeds);
        } else {
            CtExecutable<?> parentExe = getHigherExecutable(current_elem);
            if (parentExe != null) {
                ImpactChain extended = current.extend(getImpactElement(parentExe), ImpactType.EXPAND,
                        weightedMore(weight * 2));
                typeChains.add(extended);
            }
            flowChains.add(current);
        }
        return ImpactType.Level.TYPE_GRAPH;
    }

    ImpactType.Level processFlowChain() {
        ImpactChain current = flowChains.poll();
        if (current == null) {
            return ImpactType.Level.STRUCT_GRAPH;
        }
        if (current.size() > 100) {
            abortedChains.add(current);
        }
        int weight = current.getMD(ImpactChain.WEIGHT, 0);
        if (weight <= 0) {
            abortedChains.add(current);
        }

        Object best_weight_ele = current.getLast().getMD(ImpactElement.BEST_FLOW);
        // TODO redundant

        final CtElement current_elem = current.getLast().getContent();

        // TODO !!!!! implem follow

        structChains.add(current);
        return ImpactType.Level.FLOW_GRAPH;
    }

    ImpactType.Level processStructChain() {
        ImpactChain current = structChains.poll();
        if (current == null) {
            return ImpactType.Level.OTHER;
        }
        if (current.size() > 100) {
            abortedChains.add(current);
        }
        int weight = current.getMD(ImpactChain.WEIGHT, 0);
        if (weight <= 0) {
            abortedChains.add(current);
        }
        
        Object best_weight_ele = current.getLast().getMD(ImpactElement.BEST_STRUC);
        // TODO redundant

        final CtElement current_elem = current.getLast().getContent();

        // TODO !!!!! implem

        structChains.add(current);
        return ImpactType.Level.STRUCT_GRAPH;
    }

    ImpactType.Level processOtherChain() {
        ImpactChain current = otherChains.poll();
        if (current == null) {
            return null;
        }
        if (current.size() > 100) {
            abortedChains.add(current);
        }
        int weight = current.getMD(ImpactChain.WEIGHT, 0);
        if (weight <= 0) {
            abortedChains.add(current);
        }
        
        Object best_weight_ele = current.getLast().getMD(ImpactElement.BEST_OTHER);
        // TODO redundant

        final CtElement current_elem = current.getLast().getContent();

        // TODO !!!!! implem

        structChains.add(current);
        return ImpactType.Level.OTHER;
    }

    int startingOracle(ImpactChain chain) {
        return chain.getMD(ImpactChain.WEIGHT, 1);
    }

    public Explorer(ImpactAnalysis impactAnalysis, final Set<ImpactChain> impactChains, final int defaultWeight,
            final boolean getOnTests) {
        this.impactAnalysis = impactAnalysis;
        for (ImpactChain impactChain : impactChains) {
            impactChain.putMD(ImpactChain.WEIGHT, startingOracle(impactChain) + defaultWeight);
        }
        callChains.addAll(impactChains);
        this.getOnTests = getOnTests;
    }

    public void finishChain(final ImpactChain chain) {
        if (getOnTests) {
            ImpactAnalysis.logger.info("Ignoring redundant impact path");
        } else {
            finishedChains.add(chain);
        }
    }

    /**
     * Folllow usage of an executable (ref, override, overrided)
     * @param <T>
     * @param current chain
     * @param current_elem spoon element
     * @param weight
     */
    public <T> Set<ImpactChain> followUsage(final ImpactChain current, final CtExecutable<T> current_elem,
            final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        Set<CtAbstractInvocation<T>> z = this.impactAnalysis.resolver.references(current_elem);
        for (final CtAbstractInvocation<T> invocation : z) {
            if (!invocation.getPosition().isValidPosition())
                continue;
            result.add(current.extend(getImpactElement(invocation), ImpactType.CALL, weightedMore(weight - 1)));
        }
        CtExecutable<?> override = this.impactAnalysis.resolver.override(current_elem);
        if (override != null && override.getPosition().isValidPosition()) {
            result.add(current.extend(getImpactElement(override), ImpactType.OVERRIDING, weightedMore(weight - 1)));
        }
        Set<CtExecutable<?>> overrides = this.impactAnalysis.resolver.overrides(current_elem);
        for (CtExecutable<?> overri : overrides) {
            if (overri.getPosition().isValidPosition()) {
                result.add(current.extend(getImpactElement(overri), ImpactType.OVERRIDED, weightedMore(weight - 1)));
            }
        }
        return result;
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
    public <T> Set<ImpactChain> followValueArguments(final ImpactChain current,
            final CtAbstractInvocation<T> current_elem, final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        final List<CtExpression<?>> arguments = ((CtAbstractInvocation<?>) current_elem).getArguments();
        int current_argument_index = 0;
        for (final CtExpression<?> argument : arguments) {
            if (!argument.getPosition().isValidPosition())
                continue;
            final Map<String, Object> more = new HashMap<>();
            if (!argument.getType().isPrimitive()) {
                more.put("index", current_argument_index);
                result.addAll(followExprFromArgument(current, argument, more, weight));
            }
            current_argument_index++;
        }
        return result;
    }

    private Set<ImpactChain> followExprFromArgument(final ImpactChain current, final CtExpression<?> expr,
            final Map<String, Object> more, final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        if (expr instanceof CtAbstractInvocation) {
            final ImpactChain extended = current.extend(getImpactElement(expr), ImpactType.ARGUMENT, more);
            // putIfNotRedundant(extended, weight - 1); // I don't think it's a good idea to
            // put it in the queue as is :/
            // TODO Maybe follow target calling this method ?
            int current_argument_index = 0;
            for (CtExpression<?> arg : ((CtAbstractInvocation<?>) expr).getArguments()) {
                final Map<String, Object> more2 = new HashMap<>();
                more2.put("index", current_argument_index);
                result.addAll(followExprFromArgument(extended, arg, more2, weight - 1));
                current_argument_index++;
            }
            // } else if (expr instanceof CtSuperAccess) { // should allow more precise
            // things
        } else if (expr instanceof CtSuperAccess) {
            throw new RuntimeException("do not handle " + expr.getClass().getCanonicalName());
        } else if (expr instanceof CtThisAccess) { // TODO caution complex
            CtThisAccess<?> thisAccess = (CtThisAccess<?>) expr;
            CtElement parent = thisAccess.getParent();
            if (parent instanceof CtFieldAccess) {
                CtType<?> top = parent.getParent(CtType.class).getTopLevelType();
                Set<CtType<?>> implems = this.impactAnalysis.resolver
                        .referencesSuperClass((CtType<?>) parent.getParent(CtType.class)); // extends
                // TODO implements
                CtVariable<?> fieldDecl = this.impactAnalysis.resolver.reference((CtFieldAccess<?>) parent);
                if (fieldDecl != null) {
                    final ImpactChain extended = current.extend(getImpactElement(fieldDecl), ImpactType.ACCESS, more);
                    Integer weight2 = weight - 1;
                    for (CtVariableAccess<?> access : this.impactAnalysis.resolver.references((CtField<?>) fieldDecl)) {
                        if (top.hasParent(access)) {
                            result.add(extended.extend(getImpactElement(access), ImpactType.VALUE,
                                    weightedMore(weight2 - 1)));
                        }
                        if (implems != null) {
                            for (CtType<?> implem : implems) {
                                if (implem.hasParent(access)) {
                                    result.add(extended.extend(getImpactElement(access), ImpactType.VALUE,
                                            weightedMore(weight2 - 1)));
                                }
                            }
                        } else {
                            Set<CtType<?>> aaa = this.impactAnalysis.resolver
                                    .referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                        }
                    }
                }
            } else {
                assert false : parent;
            }
        } else if (expr instanceof CtTypeAccess && expr.getParent() instanceof CtFieldAccess
                && ((CtFieldAccess<?>) expr.getParent()).getVariable().getSimpleName().equals("class")) {
            return result;
        } else if (expr instanceof CtTypeAccess && expr.getParent() instanceof CtFieldAccess
                && ((CtFieldAccess<?>) expr.getParent()).getVariable().getDeclaration() != null
                && ((CtFieldAccess<?>) expr.getParent()).getVariable().getDeclaration().isFinal()) {
            return result;
        } else if (expr instanceof CtTypeAccess && expr.getParent(CtType.class)
                .equals(((CtTypeAccess<?>) expr).getAccessedType().getTypeDeclaration())) {
            CtTypeAccess<?> thisAccess = (CtTypeAccess<?>) expr;
            CtElement parent = thisAccess.getParent();
            if (parent instanceof CtFieldAccess) {
                CtType<?> top = parent.getParent(CtType.class).getTopLevelType();
                Set<CtType<?>> implems = this.impactAnalysis.resolver
                        .referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                CtVariable<?> fieldDecl = this.impactAnalysis.resolver.reference((CtFieldAccess<?>) parent);
                final ImpactChain extended = current.extend(getImpactElement(fieldDecl), ImpactType.ACCESS, more);
                Integer weight2 = weight - 1;
                for (CtVariableAccess<?> access : this.impactAnalysis.resolver.references((CtField<?>) fieldDecl)) {
                    if (top.hasParent(access)) {
                        result.add(
                                extended.extend(getImpactElement(access), ImpactType.VALUE, weightedMore(weight2 - 1)));
                    }
                    if (implems != null) {
                        for (CtType<?> implem : implems) {
                            if (implem.hasParent(access)) {
                                result.add(extended.extend(getImpactElement(access), ImpactType.VALUE,
                                        weightedMore(weight - 1)));
                            }
                        }
                    } else {
                        Set<CtType<?>> aaa = this.impactAnalysis.resolver
                                .referencesSuperClass((CtType<?>) parent.getParent(CtType.class));
                    }
                }
            } else {
                assert false : parent;
            }
        } else if (expr instanceof CtTargetedExpression) { // big approx
            CtTargetedExpression<?, ?> targeted = (CtTargetedExpression<?, ?>) expr;
            result.addAll(followExprFromArgument(current, targeted.getTarget(), more, weight - 1));
        } else if (expr instanceof CtVariableAccess) {
            CtVariable<?> x = this.impactAnalysis.resolver.reference((CtVariableAccess<?>) expr);
            if (x != null) {
                result.add(current.extend(getImpactElement(x), ImpactType.ACCESS, weightedMore(more, weight - 1)));
            }
        } else if (expr instanceof CtAssignment) {
            CtAssignment<?, ?> assign = (CtAssignment<?, ?>) expr;
            result.addAll(followExprFromArgument(current, assign.getAssigned(), more, weight - 1));
        } else if (expr instanceof CtConditional) {
            CtConditional<?> cond = (CtConditional<?>) expr;
            result.addAll(followExprFromArgument(current, cond.getThenExpression(), more, weight - 1));
            result.addAll(followExprFromArgument(current, cond.getElseExpression(), more, weight - 1));
        } else if (expr instanceof CtBinaryOperator) {
            CtBinaryOperator<?> op = (CtBinaryOperator<?>) expr;
            result.addAll(followExprFromArgument(current, op.getLeftHandOperand(), more, weight - 1));
            result.addAll(followExprFromArgument(current, op.getRightHandOperand(), more, weight - 1));
        } else if (expr instanceof CtUnaryOperator) {
            CtUnaryOperator<?> op = (CtUnaryOperator<?>) expr;
            result.addAll(followExprFromArgument(current, op.getOperand(), more, weight - 1));
        } else if (expr instanceof CtLiteral) {
            return result;
        } else {
            assert false : expr;
        }
        return result;
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

    Map<String, Object> map(String key, Object value) {
        Map<String, Object> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    Map<String, Object> weightedMore(int weight) {
        return map(ImpactChain.WEIGHT, weight);
    }

    Map<String, Object> weightedMore(Map<String, Object> old, int weight) {
        Map<String, Object> result = new HashMap<>(old);
        result.put(ImpactChain.WEIGHT, weight);
        return result;
    }

    public Set<ImpactChain> followValue(final ImpactChain current, final CtExpression<?> current_elem,
            final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        try {
            // if (current_elem instanceof CtTargetedExpression) {
            // CtVariable<?> variable = solveTargeted((CtTargetedExpression<?,?>)
            // current_elem);
            // if (variable != null) {
            // final ImpactChain extended = current.extend(getImpactElement(variable),
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
                    CtExecutable<?> parent2 = parent.getParent(CtExecutable.class);
                    if (parent2 != null) {
                        result.add(
                                current.extend(getImpactElement(parent2), ImpactType.RETURN, weightedMore(weight - 1)));
                    }
                } catch (final ParentNotInitializedException e) {
                    ImpactAnalysis.logger.log(Level.WARNING, "parentNotInitializedException", e);
                }
            } else if (parent instanceof CtVariable && roleInParent.equals(CtRole.DEFAULT_EXPRESSION)) {
                result.add(current.extend(getImpactElement(parent), ImpactType.VALUE, weightedMore(weight - 1)));
            } else if (parent instanceof CtAbstractInvocation && roleInParent.equals(CtRole.ARGUMENT)) {
                result.add(current.extend(getImpactElement(parent), ImpactType.ACCESS, weightedMore(weight - 1)));
            } else if (parent instanceof CtAbstractInvocation && roleInParent.equals(CtRole.TARGET)) {
                result.add(current.extend(getImpactElement(parent), ImpactType.VALUE, weightedMore(weight - 1)));
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
                            Set<CtType<?>> implems = this.impactAnalysis.resolver
                                    .referencesSuperClass((CtType<?>) thisAccessParent.getParent(CtType.class));
                            CtVariable<?> fieldDecl = this.impactAnalysis.resolver
                                    .reference((CtFieldAccess<?>) thisAccessParent);
                            if (fieldDecl != null) {
                                final ImpactChain extended = current.extend(getImpactElement(fieldDecl),
                                        ImpactType.VALUE, weightedMore(weight - 1));
                                result.add(extended);
                                Integer weight2 = extended.getMD(ImpactChain.WEIGHT);
                                for (CtVariableAccess<?> access : this.impactAnalysis.resolver
                                        .references((CtField<?>) fieldDecl)) {
                                    if (top.hasParent(access)) {
                                        result.add(current.extend(getImpactElement(access), ImpactType.VALUE,
                                                weightedMore(weight2 - 1)));
                                    }
                                    for (CtType<?> implem : implems) {
                                        if (implem.hasParent(access)) {
                                            result.add(extended.extend(getImpactElement(access), ImpactType.VALUE,
                                                    weightedMore(weight - 1)));
                                        }
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
                        result.add(current.extend(getImpactElement(this.impactAnalysis.resolver.reference(unnested)),
                                ImpactType.VALUE, weightedMore(weight - 1)));
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
                // return;
            } else if (parent instanceof CtConditional && roleInParent.equals(CtRole.CONDITION)) {
                // return;
            } else if (parent instanceof CtBlock) {
                // return;
            } else if (parent instanceof CtIf && roleInParent.equals(CtRole.CONDITION)) {
                result.add(current.extend(getImpactElement(((CtIf) parent)), ImpactType.CONDITION,
                        weightedMore(weight - 5)));
                return result;
            } else if (parent instanceof CtIf && roleInParent.equals(CtRole.THEN)) {
                result.add(
                        current.extend(getImpactElement(((CtIf) parent)), ImpactType.THEN, weightedMore(weight - 5)));
                return result;
            } else if (parent instanceof CtIf && roleInParent.equals(CtRole.ELSE)) {
                result.add(
                        current.extend(getImpactElement(((CtIf) parent)), ImpactType.ELSE, weightedMore(weight - 5)));
                return result;
            } else if (parent instanceof CtLoop && roleInParent.equals(CtRole.BODY)) {
                result.add(
                        current.extend(getImpactElement(((CtLoop) parent)), ImpactType.THEN, weightedMore(weight - 5)));
                return result;
            } else if (parent instanceof CtForEach && roleInParent.equals(CtRole.EXPRESSION)) {
                result.add(current.extend(getImpactElement(((CtForEach) parent).getVariable()), ImpactType.VALUE,
                        weightedMore(weight - 1)));
                return result;
            } else if (parent instanceof CtFor && roleInParent.equals(CtRole.EXPRESSION)) {
                result.add(current.extend(getImpactElement(((CtFor) parent)), ImpactType.CONDITION,
                        weightedMore(weight - 1)));
                return result;
            } else if (parent instanceof CtFor && roleInParent.equals(CtRole.FOR_INIT)) {
            } else if (parent instanceof CtFor && roleInParent.equals(CtRole.FOR_UPDATE)) {
                result.add(
                        current.extend(getImpactElement(((CtFor) parent)), ImpactType.VALUE, weightedMore(weight - 1)));
                return result;
            } else if (parent instanceof CtThrow) { // TODO implement something to follow value in catch clause
                result.add(current.extend(getImpactElement(((CtThrow) parent)), ImpactType.THROW,
                        weightedMore(weight - 1)));
                return result;
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
                ImpactAnalysis.logger.log(Level.WARNING,
                        "followValue case not handled: " + parent.getClass() + " " + roleInParent.name());
                assert false : parent;
                /**
                 * CtForImpl EXPRESSION CtFieldWriteImpl TARGET CtAssignmentImpl ASSIGNED
                 */
            }

            if (parent instanceof CtExpression) {
                followValue(current, (CtExpression<?>) parent, weight - 1);
            } else {
                CtElement parentAlt = current_elem.getParent(CtExecutable.class);
                if (parentAlt != null) {
                    result.add(current.extend(getImpactElement((parentAlt)), ImpactType.EXPAND,
                            weightedMore(weight - 10)));
                }
            }
        } catch (final ParentNotInitializedException e) {
            ImpactAnalysis.logger.log(Level.WARNING, "parentNotInitializedException", e);
        }
        return result;
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

    // public void expandToScopeOtherwiseExecutableOtherwiseType(final ImpactChain current, final CtElement current_elem,
    //         final Integer weight) {
    //     try {
    //         final CtCodeElement parentScopeBlock = current_elem.getParent(scopeFilter);
    //         if (parentScopeBlock != null) {
    //             if (parentScopeBlock instanceof CtType) {
    //                 proposeCommit(current.extend(getImpactElement(parentScopeBlock), ImpactType.DIRECT_EXPAND,
    //                         weightedMore(weight - 1)));
    //             } else if (parentScopeBlock instanceof CtExecutable) {
    //                 proposeCommit(current.extend(getImpactElement(parentScopeBlock), ImpactType.DIRECT_EXPAND,
    //                         weightedMore(weight - 1)));
    //             } else if (parentScopeBlock instanceof CtBlock) {
    //                 final CtElement parentEle = parentScopeBlock.getParent();
    //                 if (parentEle == null) {
    //                     proposeCommit(current.extend(getImpactElement(parentScopeBlock), ImpactType.DIRECT_EXPAND,
    //                             weightedMore(weight - 1)));
    //                 } else if (parentEle instanceof CtBlock) {
    //                     proposeCommit(current.extend(getImpactElement(parentScopeBlock), ImpactType.DIRECT_EXPAND,
    //                             weightedMore(weight - 1)));
    //                 } else if (parentEle instanceof CtCodeElement && scopeFilter.matches((CtCodeElement) parentEle)) {
    //                     proposeCommit(current.extend(getImpactElement(parentEle), ImpactType.DIRECT_EXPAND,
    //                             weightedMore(weight - 1)));
    //                 } else {
    //                     proposeCommit(current.extend(getImpactElement(parentEle), ImpactType.DIRECT_EXPAND,
    //                             weightedMore(weight - 1)));
    //                 }
    //             } else {
    //                 proposeCommit(current.extend(getImpactElement(parentScopeBlock), ImpactType.DIRECT_EXPAND,
    //                         weightedMore(current_elem instanceof CtExecutable ? 0 : weight - 10)));
    //             }
    //         } else {
    //             finishChain(current);
    //         }
    //     } catch (final ParentNotInitializedException e) {
    //         ImpactAnalysis.logger.info("ParentNotInitializedException");
    //     }
    //     // TODO expand to type (class for example as a modifier of a class or an extends
    //     // or an implements)

    //     // TODO how should @override or abstract be handled (virtual call)
    // }

    public Set<ImpactChain> followUses(final ImpactChain current, final CtType<?> current_elem, final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        Set<CtType<?>> superClasses = this.impactAnalysis.resolver.referencesSuperClass(current_elem);
        Set<CtType<?>> superInterfaces = this.impactAnalysis.resolver.referencesSuperInterface(current_elem);
        Set<CtTypedElement<?>> allUses = this.impactAnalysis.resolver.references(current_elem);

        for (final CtType<?> type : superClasses) {
            if (!type.getPosition().isValidPosition())
                continue;
            result.add(current.extend(getImpactElement(type), ImpactType.EXTEND, weightedMore(weight - 1)));
        }
        for (final CtType<?> type : superInterfaces) {
            if (!type.getPosition().isValidPosition())
                continue;
            result.add(current.extend(getImpactElement(type), ImpactType.IMPLEMENT, weightedMore(weight - 1)));

        }
        for (final CtTypedElement<?> type : allUses) {
            if (!type.getPosition().isValidPosition())
                continue;
            result.add(current.extend(getImpactElement(type), ImpactType.TYPE, weightedMore(weight - 1)));
        }
        return result;
    }

    // public void followTypes(final ImpactChain current, final CtTypedElement
    // current_elem, final Integer weight)
    // {
    // final CtTypeReference<?> typeRef = current_elem.getType();
    // final Object z = current_elem.getMetadata("type");
    // if (z == null) {
    // } else if (z instanceof Collection) {
    // final Collection<?> a = (Collection<?>) z;
    // for (final Object b : a) {
    // final CtType<?> type = (CtType<?>) b;
    // if (!type.getPosition().isValidPosition())
    // continue;
    // final ImpactChain extended = current.extend(getImpactElement(type), "type");
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
    // {
    // // final CtQuery q = launcher.getFactory().createQuery();
    // // final Set<CtVariableRead> s = new
    // // HashSet(q.setInput(current_elem).list(CtVariableRead.class));
    // // for (final CtVariableRead x : s) {
    // // if (!x.getPosition().isValidPosition())
    // // continue;
    // // final ImpactChain extended = current.extend(getImpactElement(x), "read");
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
    // final ImpactChain extended = current.extend(getImpactElement(x), "read");
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
    // // final ImpactChain extended = current.extend(getImpactElement(x), "read");
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
    // {
    // }

    public <T> Set<ImpactChain> followVariableValueAndUses(final ImpactChain current, final CtVariable<T> current_elem,
            final Integer weight) {
        Set<ImpactChain> result = new HashSet<>();
        // if (current_elem instanceof CtLocalVariable) {
        // final CtLocalVariable<?> tmp = (CtLocalVariable<?>) current_elem;
        // final CtExecutable<?> enclosingScope = tmp.getParent(CtExecutable.class);
        // final CtQuery q = launcher.getFactory().createQuery();Write
        // if (!x.getPosition().isValidPosition())
        // continue;
        // final ImpactChain extended = current.extend(getImpactElement(x), "write");
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
        Set<CtVariableAccess<T>> s = this.impactAnalysis.resolver.references(current_elem);
        for (final CtVariableAccess<T> x : s) {
            if (!x.getPosition().isValidPosition())
                continue;
            if (x instanceof CtVariableRead) {
                result.add(current.extend(getImpactElement(x), ImpactType.READ, weightedMore(weight - 1)));
            }
        }
        return result;
    }
}