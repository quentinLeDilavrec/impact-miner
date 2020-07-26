package fr.quentin.impactMiner;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Resolver
 */
public class Resolver {

    public Resolver(final Collection<CtType<?>> allTypes) {
        initTypes(allTypes);
    }

    private static final String METADATA_KEY_REVERSE = "reversed";// + UUID.randomUUID();
    private static final String METADATA_KEY_SUPER_CLASSES = "superClass";// + UUID.randomUUID();
    private static final String METADATA_KEY_SUPER_INTERFACES = "superInterface";// + UUID.randomUUID();
    private static final String METADATA_KEY_INVS_COUNT = "invsCount";// + UUID.randomUUID();
    private static final String METADATA_KEY_REVERSE_COUNT = "accessCount";// + UUID.randomUUID();
    private static final String METADATA_KEY_TYPED_COUNT = "typedCount";// + UUID.randomUUID();
    private static final String METADATA_KEY_TYPED = "typed";// + UUID.randomUUID();
    private static final String METADATA_KEY_OVERRIDES = "overrides";// + UUID.randomUUID();
    private static final String METADATA_KEY_OVERRIDES_COUNT = "overridesCount";// + UUID.randomUUID();

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

    private static <T> Uses<T> makeUses(Class<T> clazz) {
        return new Uses<T>(clazz);
    }

    // @SuppressWarnings("unchecked")
    // private <T> void insertMetaData(final CtElement element, final String key,
    // final Uses<T> defaultValue,
    // final T value) {
    // Object x = element.getMetadata(key);
    // if (x == null) {
    // x = defaultValue;
    // element.putMetadata(key, x);
    // }
    // if (x instanceof Uses) {
    // ((Uses<T>) x).add(value);
    // }
    // }

    @SuppressWarnings("unchecked")
    private <T> void insertMetaData(final CtElement element, final String key, final T value) {
        Object x = element.getMetadata(key);
        if (x == null) {
            x = makeUses(value.getClass());
            element.putMetadata(key, x);
        }
        if (x instanceof Uses) {
            ((Uses<T>) x).add(value);
        }
    }

    private void initTypes(final Collection<CtType<?>> allTypes) {
        for (final CtType<?> type : allTypes) {
            for (final CtTypeReference<?> typeRef : type.getUsedTypes(false)) {
                insertMetaData(typeRef, Resolver.METADATA_KEY_REVERSE, type);
            }
            final CtTypeReference<?> superClass = type.getSuperclass();
            insertMetaData(superClass, Resolver.METADATA_KEY_SUPER_CLASSES, type);
            final Set<CtTypeReference<?>> superInterfaces = type.getSuperInterfaces();
            for (final CtTypeReference<?> superInterface : superInterfaces) {
                insertMetaData(superInterface, Resolver.METADATA_KEY_SUPER_INTERFACES, type);
            }
        }
    }

    private <T> void initTypesForTyped(final CtType<?> declaringType) {
        final Object types = declaringType.getReference().getMetadata(Resolver.METADATA_KEY_REVERSE);
        if (types != null && types instanceof Uses) {
            assert ((Uses) types).getType().equals(CtType.class);
            @SuppressWarnings("unchecked")
            Set<CtType<Object>> tops = ((Uses<CtType<?>>) types).getValues().stream().map(x -> x.getTopLevelType())
                    .collect(Collectors.toSet());
            for (final CtType<?> type : tops) {
                final Object exe_count = type.getReference().getMetadata(Resolver.METADATA_KEY_TYPED_COUNT);
                if (exe_count == null || !(exe_count instanceof Integer)) {
                    final List<CtTypedElement<?>> typeds = type.getElements(new TypeFilter<>(CtTypedElement.class));
                    type.getReference().putMetadata(Resolver.METADATA_KEY_TYPED_COUNT, typeds.size());
                    for (final CtTypedElement<?> typed : typeds) {
                        insertMetaData(typed.getType(), Resolver.METADATA_KEY_TYPED, typed);
                    }
                }
            }
        }

    }

    private void initExecutables(final CtType<?> declaringType) {
        final Object types = declaringType.getReference().getMetadata(Resolver.METADATA_KEY_REVERSE);
        if (types != null && types instanceof Uses) {
            assert ((Uses) types).getType().equals(CtType.class);
            @SuppressWarnings("unchecked")
            Set<CtType<Object>> tops = ((Uses<CtType<?>>) types).getValues().stream().map(x -> x.getTopLevelType())
                    .collect(Collectors.toSet());
            for (final CtType<?> type : tops) {
                final Object exe_count = type.getReference().getMetadata(Resolver.METADATA_KEY_INVS_COUNT);
                if (exe_count == null || !(exe_count instanceof Integer)) {
                    final List<CtAbstractInvocation<?>> invs = type
                            .getElements(new TypeFilter<>(CtAbstractInvocation.class));
                    type.getReference().putMetadata(Resolver.METADATA_KEY_INVS_COUNT, invs.size());
                    for (final CtAbstractInvocation<?> inv : invs) {
                        insertMetaData(inv.getExecutable(), Resolver.METADATA_KEY_REVERSE, inv);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initOverrides(final CtType<?> declaringType) {
        final Object classes = declaringType.getReference().getMetadata(Resolver.METADATA_KEY_SUPER_CLASSES);
        if (classes != null && classes instanceof Uses) {
            initOverridesAux((Uses<CtType<?>>) classes);
        }
        final Object interfaces = declaringType.getReference().getMetadata(Resolver.METADATA_KEY_SUPER_INTERFACES);
        if (interfaces != null && interfaces instanceof Uses) {
            initOverridesAux((Uses<CtType<?>>) interfaces);
        }
    }

    private void initOverridesAux(final Uses<CtType<?>> types) {
        assert types.getType().equals(CtType.class);
        Set<CtType<Object>> tops = types.getValues().stream().map(x -> x.getTopLevelType()).collect(Collectors.toSet());
        for (final CtType<?> type : tops) {
            final Object override_count = type.getReference().getMetadata(Resolver.METADATA_KEY_OVERRIDES_COUNT);
            if (override_count == null || !(override_count instanceof Integer)) {
                final List<CtExecutable<?>> overriders = type.getElements(new Filter<CtExecutable<?>>() {

                    @Override
                    public boolean matches(CtExecutable<?> element) {
                        return element.getReference().getOverridingExecutable() != null;
                    }

                });
                type.getReference().putMetadata(Resolver.METADATA_KEY_OVERRIDES_COUNT, overriders.size());
                for (final CtExecutable<?> exe : overriders) {
                    insertMetaData(exe.getReference().getOverridingExecutable(), Resolver.METADATA_KEY_OVERRIDES, exe);
                }
            }
        }
    }

    private void initVariables(final CtVariable<?> declaringElement) {

        // if (declaringElement instanceof CtField && ((CtField)
        // declaringElement).isPrivate()) {
        if ((declaringElement instanceof CtField && !((CtField<?>) declaringElement).isPrivate())) {
            // everywhere the declaring type is used including own declaring types
            // should precompute it like executables

            final CtType<?> parentType = declaringElement.getParent(CtType.class);
            final Object types = parentType.getMetadata(Resolver.METADATA_KEY_REVERSE);
            @SuppressWarnings("unchecked")
            Set<CtType<?>> tops = ((Uses<CtType<?>>) types).getValues().stream().map(x -> x.getTopLevelType())
                    .collect(Collectors.toSet());
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
        final Object access_count = parentType.getMetadata(Resolver.METADATA_KEY_REVERSE_COUNT);
        if (access_count == null || !(access_count instanceof Integer)) {
            final List<CtVariableAccess<?>> access = parentType.getElements(new TypeFilter<>(CtVariableAccess.class));
            access.forEach(read -> {
                insertMetaData(read.getVariable(), Resolver.METADATA_KEY_REVERSE, read);
            });
            insertMetaData(parentType, Resolver.METADATA_KEY_REVERSE_COUNT, access.size());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Set<CtVariable<T>> references(final CtVariable<T> declaringElement) {
        final CtVariableReference<T> ref = declaringElement.getReference();
        final Object accs = ref.getMetadata(Resolver.METADATA_KEY_REVERSE);
        if (accs == null) {
            ref.putMetadata(METADATA_KEY_REVERSE, makeUses(CtTypedElement.class));
            initVariables(declaringElement);
            return ((Uses<CtVariable<T>>) ref.getMetadata(Resolver.METADATA_KEY_REVERSE)).getValues();
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

    @SuppressWarnings("unchecked")
    public <T> Set<CtAbstractInvocation<T>> references(final CtExecutable<T> declaringElement) {
        final CtExecutableReference<T> ref = declaringElement.getReference();
        final Object invs = ref.getMetadata(Resolver.METADATA_KEY_REVERSE);
        if (invs == null) {
            ref.putMetadata(METADATA_KEY_REVERSE, makeUses(CtTypedElement.class));
            initExecutables(ref.getParent(CtType.class));
            return ((Uses<CtAbstractInvocation<T>>) ref.getMetadata(Resolver.METADATA_KEY_REVERSE)).getValues();
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

    @SuppressWarnings("unchecked")
    public <T> Set<CtExecutable<?>> overrides(final CtExecutable<T> declaringElement) {
        final CtExecutableReference<T> ref = declaringElement.getReference();
        final Object executables = ref.getMetadata(Resolver.METADATA_KEY_OVERRIDES);
        if (executables == null) {
            ref.putMetadata(METADATA_KEY_OVERRIDES, makeUses(CtTypedElement.class));
            initOverrides(ref.getParent(CtType.class));
            return ((Uses<CtExecutable<?>>) ref.getMetadata(Resolver.METADATA_KEY_OVERRIDES)).getValues();
        } else if (executables instanceof Uses) {
            return ((Uses<CtExecutable<?>>) executables).getValues();
        }
        return null;
    }

    public <T> CtExecutable<T> reference(final CtAbstractInvocation<T> referencingElement) {
        final CtExecutableReference<T> ref = referencingElement.getExecutable();
        final CtExecutable<T> r = ref.getDeclaration();
        return r;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<CtType<?>> referencesAllUsagesPerType(final CtType<T> declaringElement) {
        // declaringElement.getReference().getOverridingExecutable().putMetadata(key,
        // val);
        final Object types = declaringElement.getReference().getMetadata(Resolver.METADATA_KEY_REVERSE);
        assert types != null;
        if (types instanceof Uses) {
            return ((Uses<CtType<?>>) types).getValues();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<CtTypedElement<?>> references(final CtType<T> declaringElement) {
        // declaringElement.getReference().getOverridingExecutable().putMetadata(key,
        // val);
        CtTypeReference<T> ref = declaringElement.getReference();
        final Object typed = ref.getMetadata(Resolver.METADATA_KEY_TYPED);
        if (typed == null) {
            ref.putMetadata(METADATA_KEY_TYPED, makeUses(CtTypedElement.class));
            initTypesForTyped(ref.getParent(CtType.class));
            return ((Uses<CtTypedElement<?>>) ref.getMetadata(Resolver.METADATA_KEY_TYPED)).getValues();
        } else if (typed instanceof Uses) {
            return ((Uses<CtTypedElement<?>>) typed).getValues();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<CtType<?>> referencesSuperInterface(final CtType<T> declaringElement) {
        final Object types = declaringElement.getReference().getMetadata(Resolver.METADATA_KEY_SUPER_INTERFACES);
        assert types != null;
        if (types instanceof Uses) {
            return ((Uses<CtType<?>>) types).getValues();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> Set<CtType<?>> referencesSuperClass(final CtType<T> declaringElement) {
        final Object types = declaringElement.getReference().getMetadata(Resolver.METADATA_KEY_SUPER_CLASSES);
        assert types != null;
        if (types instanceof Uses) {
            return ((Uses<CtType<?>>) types).getValues();
        }
        return null;
    }

    public <T> CtType<?> referenceSuperClass(final CtType<T> referencingElement) {
        final CtTypeReference<?> superClass = referencingElement.getSuperclass();
        return superClass.getTypeDeclaration();
    }

    public <T> Set<CtType<?>> referenceSuperInterfaces(final CtType<T> referencingElement) {
        final Set<CtTypeReference<?>> superInterfaces = referencingElement.getSuperInterfaces();

        return superInterfaces.stream().map(x -> x.getTypeDeclaration()).collect(Collectors.toSet());
    }

}