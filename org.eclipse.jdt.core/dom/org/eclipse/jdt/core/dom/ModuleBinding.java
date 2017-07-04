/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.jdt.core.dom;

import java.util.LinkedHashMap;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IModule.IPackageExport;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.util.Util;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.NameLookup.Answer;
import org.eclipse.jdt.internal.core.SearchableEnvironment;

/**
 * Internal implementation of module bindings.
 * @since 3.13 BETA_JAVA9
 */
class ModuleBinding implements IModuleBinding {

	protected static final ITypeBinding[] NO_TYPE_BINDINGS = new ITypeBinding[0];
	private String name = null;
	private volatile String key;
	private boolean isOpen = false;

	private org.eclipse.jdt.internal.compiler.lookup.ModuleBinding binding;
	protected BindingResolver resolver;

	private IAnnotationBinding[] annotations;
	private IModuleBinding[] requiredModules;
	private IPackageBinding[] exports; // cached
	private IPackageBinding[] opens; // cached
	private ITypeBinding[] services; // cached
	private LinkedHashMap<IPackageBinding, String[]> exportsStore;
	private LinkedHashMap<IPackageBinding, String[]> opensStore;
	private LinkedHashMap<ITypeBinding, ITypeBinding[]> servicesStore;

	ModuleBinding(BindingResolver resolver, org.eclipse.jdt.internal.compiler.lookup.ModuleBinding binding) {
		this.resolver = resolver;
		this.binding = binding;
		this.isOpen = binding.isOpen;
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		if (this.annotations == null) {
			this.annotations = resolveAnnotationBindings(this.binding.getAnnotations());
		}
		return this.annotations;
	}

	private IAnnotationBinding[] resolveAnnotationBindings(org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding[] internalAnnotations) {
		int length = internalAnnotations == null ? 0 : internalAnnotations.length;
		if (length != 0) {
			IAnnotationBinding[] tempAnnotations = new IAnnotationBinding[length];
			int convertedAnnotationCount = 0;
			for (int i = 0; i < length; i++) {
				org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding internalAnnotation = internalAnnotations[i];
				if (internalAnnotation == null)
					break;
				IAnnotationBinding annotationInstance = this.resolver.getAnnotationInstance(internalAnnotation);
				if (annotationInstance == null)
					continue;
				tempAnnotations[convertedAnnotationCount++] = annotationInstance;
			}
			if (convertedAnnotationCount != length) {
				if (convertedAnnotationCount == 0) {
					return this.annotations = AnnotationBinding.NoAnnotations;
				}
				System.arraycopy(tempAnnotations, 0, (tempAnnotations = new IAnnotationBinding[convertedAnnotationCount]), 0, convertedAnnotationCount);
			}
			return tempAnnotations;
		}
		return AnnotationBinding.NoAnnotations;
	}

	@Override
	public String getName() {
		if (this.name == null) {
			char[] tmp = this.binding.moduleName;	
			return tmp != null && tmp.length != 0 ? new String(tmp) : Util.EMPTY_STRING;
		}
		return this.name;
	}

	@Override
	public int getModifiers() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isDeprecated() {
		return false;
	}

	@Override
	public boolean isRecovered() {
		return false;
	}

	@Override
	public boolean isSynthetic() {
		// TODO Auto-generated method stub
		// TODO BETA_JAVA9 no reference seen in jvms draft - only in sotm
		// check on version change and after compiler ast implements isSynthetic return this.binding.isSynthetic();
		
		return false;
	}

	@Override
	public IJavaElement getJavaElement() {
		INameEnvironment nameEnvironment = this.binding.environment.nameEnvironment;
		if (!(nameEnvironment instanceof SearchableEnvironment)) return null;
		NameLookup nameLookup = ((SearchableEnvironment) nameEnvironment).nameLookup;
		if (nameLookup == null) return null;
		Answer answer = nameLookup.findModule(this.getName());
		if (answer == null) return null;
		return answer.module;
	}

	@Override
	public String getKey() {
		if (this.key == null) {
			char[] k = this.binding.computeUniqueKey();
			this.key = k == null || k == CharOperation.NO_CHAR ? Util.EMPTY_STRING : new String(k);
		}
		return this.key;
	}

	@Override
	public boolean isEqualTo(IBinding other) {
		if (other == this) // identical binding - equal (key or no key)
			return true;
		if (other == null) // other binding missing
			return false;

		if (!(other instanceof ModuleBinding))
			return false;

		org.eclipse.jdt.internal.compiler.lookup.ModuleBinding otherBinding = ((ModuleBinding) other).binding;
		return BindingComparator.isEqual(this.binding, otherBinding);
	}

	@Override
	public boolean isOpen() {
		return this.isOpen;
	}
	@Override
	public IModuleBinding[] getRequiredModules() {
		if (this.requiredModules != null)
			return this.requiredModules;

		org.eclipse.jdt.internal.compiler.lookup.ModuleBinding[] reqs = this.binding.getAllRequiredModules();	
		IModuleBinding[] result = new IModuleBinding[reqs != null ? reqs.length : 0];
		for (int i = 0, l = result.length; i < l; ++i) {
			org.eclipse.jdt.internal.compiler.lookup.ModuleBinding req = reqs[i];
			result[i] = req != null ? this.resolver.getModuleBinding(req) : null;
		}
		return this.requiredModules = result;
	}

	interface IVisibilePackage {
		org.eclipse.jdt.internal.compiler.lookup.PackageBinding getPack(char[] name);
	}
	private LinkedHashMap<IPackageBinding, String[]> getPacks(IPackageExport[] packs, IVisibilePackage ivp) {
		LinkedHashMap<IPackageBinding, String[]> packMap = new LinkedHashMap<>();
		for (IPackageExport pack : packs) {
			org.eclipse.jdt.internal.compiler.lookup.PackageBinding packB = ivp.getPack(pack.name());
			IPackageBinding p = packB != null ? this.resolver.getPackageBinding(packB) : null;	
			if (p != null) {
				packMap.put(p, CharOperation.toStrings(pack.targets()));
			}
		}
		return packMap;
	}

	@Override
	public IPackageBinding[] getExportedPackages() {
		if (this.exportsStore == null) {
			this.exportsStore = getPacks(this.binding.exports, this.binding :: getExportedPackage);
			this.exports = this.exportsStore.keySet().toArray(new IPackageBinding[0]);
		}
		return this.exports;
	}

	@Override
	public String[] getExportedTo(IPackageBinding packageBinding) {
		getExportedPackages();
		return this.exportsStore.get(packageBinding);
	}

	@Override
	public IPackageBinding[] getOpenedPackages() {
		if (this.opensStore == null) {
			this.opensStore = getPacks(this.binding.opens, this.binding :: getOpenedPackage);
			this.opens = this.opensStore.keySet().toArray(new IPackageBinding[0]);
		}
		return this.opens;
	}

	@Override
	public String[] getOpenedTo(IPackageBinding packageBinding) {
		getOpenedPackages();
		return this.opensStore.get(packageBinding);
	}

	/*
	 * helper method
	 */
	private ITypeBinding[] getTypes(org.eclipse.jdt.internal.compiler.lookup.TypeBinding[] types) {
		int length = types == null ? 0 : types.length;
		TypeBinding[] result = new TypeBinding[length];
		for (int i = 0; i < length; ++i) {
			result[i] = (TypeBinding) this.resolver.getTypeBinding(types[i]);
		}
		return result;
	}

	@Override
	public ITypeBinding[] getUses() {
		return getTypes(this.binding.getUses());
	}

	@Override
	public ITypeBinding[] getServices() {
		if (this.servicesStore == null) {
			this.servicesStore = new LinkedHashMap<>();
			for (org.eclipse.jdt.internal.compiler.lookup.ModuleBinding.Service cs : this.binding.getServices()) {
				ITypeBinding s = this.resolver.getTypeBinding(cs.service);
				if (s != null) {
					this.servicesStore.put(s, getTypes(cs.implementations));
				}
			}
			this.services = this.servicesStore.keySet().toArray(new ITypeBinding[0]);
		}
		return this.services;
	}
	@Override
	public ITypeBinding[] getImplementations(ITypeBinding service) {
		getServices();
		return this.servicesStore.get(service);
	}


	/**
	 * For debugging purpose only.
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return this.binding.toString();
	}
}