package fr.quentin.impactMiner;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spoon.MavenLauncher;
import spoon.SpoonAPI;
import spoon.SpoonException;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class AugmentedAST<T extends SpoonAPI> {
	public final T launcher;
    public final Path rootFolder;
    final Set<Path> testDirs;
    final Set<Path> srcDirs;
    final Set<CtType<?>> testThings = new HashSet<>();
    final Set<CtType<?>> srcThings = new HashSet<>();
    final List<CtExecutableReference<?>> allExecutablesReferences;
    final Map<String, CtType<?>> topsByFileName = new HashMap<>();

    public Map<String, CtType<?>> getTypesIndexByFileName() {
        return Collections.unmodifiableMap(topsByFileName);
    }
    public CtType<?> getTop(String path) {
        return topsByFileName.get(path);
    }

    public AugmentedAST(final T launcher) {
		this.launcher = launcher;

        this.testDirs = new HashSet<>();
        this.srcDirs = new HashSet<>();
        if (launcher instanceof MavenLauncher) {
            this.rootFolder = ((MavenLauncher) launcher).getPomFile().getFileSystemParent().toPath();
            for (final File file : ((MavenLauncher) launcher).getPomFile().getTestDirectories()) {
                this.testDirs.add(rootFolder.relativize(file.toPath().toAbsolutePath()));
            }
            for (final File file : ((MavenLauncher) launcher).getPomFile().getSourceDirectories()) {
                this.srcDirs.add(rootFolder.relativize(file.toPath().toAbsolutePath()));
            }
        } else {
            throw new IllegalArgumentException(launcher.toString());
        }

        final Collection<CtType<?>> allTypes = launcher.getModel().getAllTypes();
        for (final CtType<?> type : allTypes) {
            Path relativized = rootFolder.relativize(type.getPosition().getFile().toPath().toAbsolutePath());
            boolean isTest = false;
            for (final Path file : testDirs) {
                if (relativized.startsWith(file)) {
                    isTest = true;
                    break;
                }
            }
            if (isTest)
                this.testThings.add(type);

            boolean isNotTest = false;
            for (final Path file : srcDirs) {
                if (relativized.startsWith(file)) {
                    isNotTest = true;
                    break;
                }
            }
            if (isNotTest)
                this.srcThings.add(type);

            if (type.isTopLevel() && !type.isShadow()) {
                final CtType<?> tmp = this.topsByFileName.put(relativized.toString(), type);
                if (tmp != null) {
                    System.out.println("put return should be null "+ tmp);
                    // throw new RuntimeException("sould be null "+ tmp);
                };
            }
        }

        this.allExecutablesReferences = new ArrayList<>();
        for (final CtExecutable<?> e : this.launcher.getModel().getElements(new TypeFilter<>(CtExecutable.class))) {
            try {
                if (e!=null) {
                    this.allExecutablesReferences.add(e.getReference());
                }
            } catch (SpoonException exc) {
                exc.printStackTrace();
            }
        }
    }



    public void needsSimple(CtType<?> ele, Set<CtType<?>> acc) {
        Set<CtTypeReference<?>> l = ele.getUsedTypes(true);
        for (CtTypeReference<?> ref : l) {
            CtType<?> decl = ref.getTypeDeclaration();
            if (decl == null) {

            } else if (!acc.contains(ele)) {
                acc.add(decl);
                if (decl.isShadow()) {

                } else {
                    needsSimple(ele, acc);
                }
            }
        }
    }

    static private String META_KEY_NEEDS = "need.needs";

    // TODO get stricter needs by matching used types for any referenceable element
    // the problem with such strict filtering is the later habilities to instanciate
    // such tight ast

    public Set<CtType> needs(CtElement ele) {
        return needsDyn(ele.getParent(new TypeFilter<CtType<?>>(CtType.class)).getTopLevelType());
    }

    public Set<CtType> needsDyn(CtType<?> ele) {
        Uses<CtType> md = (Uses<CtType>) ele.getMetadata(META_KEY_NEEDS);
        assert md instanceof Uses;
        if (md != null) {
            return md.getValues();
        } else {
            md = new Uses<CtType>(CtType.class);
            ele.putMetadata(META_KEY_NEEDS, md);
        }
        Set<CtTypeReference<?>> l = ele.getUsedTypes(true);
        for (CtTypeReference<?> ref : l) {
            CtType<?> decl = ref.getTypeDeclaration();
            if (decl == null) {

            } else if (!md.contains(ele)) {
                if (decl.isShadow()) {
                    md.add(decl);
                } else {
                    md.addAll(needsDyn(ele));
                }
            }
        }
        return md.getValues();
    }

}