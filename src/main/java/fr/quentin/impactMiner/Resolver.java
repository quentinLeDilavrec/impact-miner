package fr.quentin.impactMiner;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import fr.quentin.impactMiner.ImpactAnalysis.Uses;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Resolver
 */
public class Resolver {

    Resolver(final Collection<CtType<?>> allTypes) {
        initTypes(allTypes);
    }

    private <T> void insertMetaData(final CtElement element, final String key, final Uses<T> defaultValue,
            final T value) {
        Object x = element.getMetadata(key);
        if (x == null) {
            x = defaultValue;
            element.putMetadata(key, x);
        }
        if (x instanceof Uses) {
            ((Uses<T>) x).add(value);
        }
    }

    private void initTypes(final Collection<CtType<?>> allTypes) {
        for (final CtType<?> type : allTypes) {
            for (final CtTypeReference<?> typeRef : type.getUsedTypes(false)) {
                final Uses<CtType> uses = new Uses<CtType>(CtType.class);
                insertMetaData(typeRef, ImpactAnalysis.METADATA_KEY_REVERSE, uses, type);
            }
            final CtTypeReference<?> a = type.getSuperclass();
            a.putMetadata(ImpactAnalysis.METADATA_KEY_EXTENDS, type);
            final Set<CtTypeReference<?>> b = type.getSuperInterfaces();
            for (final CtTypeReference<?> c : b) {
                final Uses<CtType> uses2 = new Uses<CtType>(CtType.class);
                insertMetaData(c, ImpactAnalysis.METADATA_KEY_IMPLEMENTS, uses2, type);
            }
        }
    }

    private void initExecutables(final CtType<?> declaringType) {
        final Object types = declaringType.getReference().getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE);
        if (types != null && types instanceof Uses) {
            assert ((Uses) types).getType().equals(CtType.class);
            Set<CtType<Object>> tops = ((Uses<CtType>) types).getValues().stream().map(x->x.getTopLevelType()).collect(Collectors.toSet());
            for (final CtType<?> type : tops) {
                final Object exe_count = type.getReference().getMetadata(ImpactAnalysis.METADATA_KEY_INVS_COUNT);
                if (exe_count == null || !(exe_count instanceof Integer)) {
                    final List<CtAbstractInvocation> invs = type
                            .getElements(new TypeFilter<>(CtAbstractInvocation.class));
                    type.getReference().putMetadata(ImpactAnalysis.METADATA_KEY_INVS_COUNT, invs.size());
                    for (final CtAbstractInvocation<?> inv : invs) {
                        final Uses<CtAbstractInvocation> uses = new Uses<CtAbstractInvocation>(
                                CtAbstractInvocation.class);
                        insertMetaData(inv.getExecutable(), ImpactAnalysis.METADATA_KEY_REVERSE, uses, inv);
                    }
                }
            }
        }
    }

    private void initVariables(final CtVariable<?> declaringElement) {

        // if (declaringElement instanceof CtField && ((CtField)
        // declaringElement).isPrivate()) {
        if ((declaringElement instanceof CtField && !((CtField) declaringElement).isPrivate())) {
            // everywhere the declaring type is used including own declaring types
            // should precompute it like executables

            final CtType<?> parentType = declaringElement.getParent(CtType.class);
            final Object types = parentType.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE);
            Set<CtType<Object>> tops = ((Uses<CtType>) types).getValues().stream().map(x->x.getTopLevelType()).collect(Collectors.toSet());
            for (final CtType<?> type : tops) {
                initVariables(type);
            }
            // } else if (declaringElement instanceof CtLocalVariable || declaringElement
            // instanceof CtCatchVariable
            // || declaringElement instanceof CtParameter) {
            // final CtExecutable<?> parentExecutable =
            // declaringElement.getParent(CtExecutable.class);
            // Object access_count =
            // parentExecutable.getMetadata(METADATA_KEY_REVERSE_COUNT);
            // if (access_count == null || !(access_count instanceof Integer)) {
            // List<CtVariableAccess> access = parentExecutable.getElements(new
            // TypeFilter(CtVariableAccess.class));
            // access.forEach(read -> {
            // insertMetaData(read.getVariable(), METADATA_KEY_ACCES,
            // new Uses<CtVariableAccess>(CtVariableAccess.class), read);
            // });
            // insertMetaData(parentExecutable, METADATA_KEY_REVERSE_COUNT, new
            // Uses<Integer>(Integer.class),
            // access.size());
            // }
        } else {// CtTypeMember
            // own declaring types
            final CtType<?> parentType = declaringElement.getParent(CtType.class).getTopLevelType();
            initVariables(parentType);
        }
    }

    private void initVariables(final CtType<?> parentType) {
        final Object access_count = parentType.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE_COUNT);
        if (access_count == null || !(access_count instanceof Integer)) {
            final List<CtVariableAccess> access = parentType.getElements(new TypeFilter(CtVariableAccess.class));
            access.forEach(read -> {
                insertMetaData(read.getVariable(), ImpactAnalysis.METADATA_KEY_REVERSE,
                        new Uses<CtVariableAccess>(CtVariableAccess.class), read);
            });
            insertMetaData(parentType, ImpactAnalysis.METADATA_KEY_REVERSE_COUNT, new Uses<Integer>(Integer.class), access.size());
        }
    }

    public <T> Set<CtVariable<T>> references(final CtVariable<T> declaringElement) {
        final CtVariableReference<T> ref = declaringElement.getReference();
        final Object accs = ref.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE);
        if (accs == null) {
            initVariables(declaringElement);
            return ((Uses<CtVariable<T>>) ref.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE)).getValues();
        } else if (accs instanceof Uses) {
            return ((Uses<CtVariable<T>>) accs).getValues();
        }
        return null;
    }

    public <T> CtVariable<T> reference(final CtVariableAccess<T> referencingElement) {
        final CtVariableReference<T> ref = referencingElement.getVariable();
        final CtVariable<T> r = ref.getDeclaration();
        return r;
    }

    public <T> Set<CtAbstractInvocation<T>> references(final CtExecutable<T> declaringElement) {
        final CtExecutableReference<T> ref = declaringElement.getReference();
        final Object invs = ref.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE);
        if (invs == null) {
            initExecutables(ref.getParent(CtType.class));
            return ((Uses<CtAbstractInvocation<T>>) ref.getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE)).getValues();
        } else if (invs instanceof Uses) {
            return ((Uses<CtAbstractInvocation<T>>) invs).getValues();
        }
        return null;
    }

    public <T> CtExecutable<?> override(final CtExecutable<T> declaringElement) {
        final CtExecutableReference<T> ref = declaringElement.getReference();
        final CtExecutableReference<?> override = ref.getOverridingExecutable();
        if (override != null) {
            return override.getDeclaration();
        }
        return null;
    }

    public <T> CtExecutable<T> reference(final CtAbstractInvocation<T> referencingElement) {
        final CtExecutableReference<T> ref = referencingElement.getExecutable();
        final CtExecutable<T> r = ref.getDeclaration();
        return r;
    }

    public <T> Set<CtType<?>> references(final CtType<T> declaringElement) {
        // declaringElement.getReference().getOverridingExecutable().putMetadata(key,
        // val);
        final Object types = declaringElement.getReference().getMetadata(ImpactAnalysis.METADATA_KEY_REVERSE);
        assert types != null;
        if (types instanceof Uses) {
            return ((Uses<CtType<?>>) types).getValues();
        }
        return null;
    }

    public <T> Set<CtType<?>> referencesSuperInterfaces(final CtType<T> declaringElement) {
        // declaringElement.getReference().getOverridingExecutable().putMetadata(key,
        // val);
        final Object types = declaringElement.getReference().getMetadata(ImpactAnalysis.METADATA_KEY_IMPLEMENTS);
        assert types != null;
        if (types instanceof Uses) {
            return ((Uses<CtType<?>>) types).getValues();
        }
        return null;
    }

    public <T> CtType<?> referenceSuperClass(final CtType<T> declaringElement) {
        final Object types = declaringElement.getReference().getMetadata(ImpactAnalysis.METADATA_KEY_EXTENDS);
        if (types != null && types instanceof CtType) {
            return (CtType<?>) types;
        }
        return null;
    }

    // public <T> Set<CtType<?>> reference(final CtType<T> referencingElement) {
    //     return referencingElement.getUsedTypes(false).stream().map(x -> x.getDeclaration())
    //             .collect(Collectors.toSet());
    // }

}