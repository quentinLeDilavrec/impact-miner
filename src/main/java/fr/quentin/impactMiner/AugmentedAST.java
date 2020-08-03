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
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class AugmentedAST<T extends SpoonAPI> {
	public final T launcher;
    public final Path rootFolder;
    final Set<Path> testDirs;
    final Set<Path> srcDirs;
    final Set<CtType<?>> testThings = new HashSet<>();
    final Set<CtType<?>> srcThings = new HashSet<>();
    final List<CtExecutableReference<?>> allExecutablesReferences;
    final Map<String, CtType<?>> typesIndexByFileName = new HashMap<>();

    public Map<String, CtType<?>> getTypesIndexByFileName() {
        return Collections.unmodifiableMap(typesIndexByFileName);
    }
    public CtType<?> getTop(String path) {
        return typesIndexByFileName.get(path);
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

            if (type.isTopLevel()) {
                final CtType<?> tmp = this.typesIndexByFileName.put(relativized.toString(), type);
                assert tmp == null : tmp;
            }
        }

        this.allExecutablesReferences = new ArrayList<>();
        for (final CtExecutable<?> e : this.launcher.getModel().getElements(new TypeFilter<>(CtExecutable.class))) {
            this.allExecutablesReferences.add(e.getReference());
        }
    }

}