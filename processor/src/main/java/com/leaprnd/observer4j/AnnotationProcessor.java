package com.leaprnd.observer4j;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.leaprnd.observer4j.ForwardStrategy.DO_NOT_FORWARD;
import static com.leaprnd.observer4j.ForwardStrategy.FORWARD_AFTER_MARKING_EVERY_FIELD_AS_ACCESSED;
import static com.leaprnd.observer4j.ForwardStrategy.FORWARD_AFTER_MARKING_FIELD_AS_ACCESSED;
import static com.leaprnd.observer4j.ForwardStrategy.valueOf;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeName.BOOLEAN;
import static com.squareup.javapoet.TypeName.INT;
import static java.lang.String.format;
import static javax.lang.model.SourceVersion.RELEASE_17;
import static javax.lang.model.element.Modifier.DEFAULT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.NATIVE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.NONE;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.tools.Diagnostic.Kind.ERROR;

@SupportedSourceVersion(RELEASE_17)
public class AnnotationProcessor extends AbstractProcessor {

	private static final String IMMUTABLES_VALUE_ANNOTATION = "org.immutables.value.Value";
	private static final String IMMUTABLES_DEFAULT_ANNOTATION = IMMUTABLES_VALUE_ANNOTATION + ".Default";
	private static final String IMMUTABLES_DERIVED_ANNOTATION = IMMUTABLES_VALUE_ANNOTATION + ".Derived";
	private static final String IMMUTABLES_LAZY_ANNOTATION = IMMUTABLES_VALUE_ANNOTATION + ".Lazy";
	private static final String FORWARD_ANNOTATION = Forward.class.getName();

	private static final ClassName ABSTRACT_LISTENABLE_PROPERTIES = ClassName
		.get("com.leaprnd.observer4j", "AbstractSubstitutableForwarder");

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Collections.singleton(GenerateSubstitutableForwarder.class.getName());
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		for (final var element : roundEnv.getElementsAnnotatedWith(GenerateSubstitutableForwarder.class)) {
			if (element instanceof final TypeElement typeElement) {
				try {
					generate(typeElement);
				} catch (IOException exception) {
					error(format("Failed to write forwarder: %s.", exception), element);
				}
			} else {
				error("@GenerateSubstitutableForwarder was applied to an unexpected program element!", element);
			}
		}
		return true;
	}

	private record Specification(ClassName forwarderType, TypeElement delegateType) {
		public MethodSpec buildConstructor() {
			final var delegateTypeName = ClassName.get(delegateType);
			return constructorBuilder()
				.addModifiers(PUBLIC)
				.addParameter(delegateTypeName, "delegate")
				.addStatement("super(delegate)")
				.build();
		}
	}

	private Specification getSpecificationOf(TypeElement typeElement) {
		final var utils = getElementUtils();
		final var annotation = getGenerateSubstitutableForwarderAnnotationOf(typeElement);
		final var packageName = getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
		final var values = utils.getElementValuesWithDefaults(annotation);
		return new Specification(ClassName.get(packageName, getNameFrom(values)), getTypeFrom(values));
	}

	private String getNameFrom(Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
		return (String) getValue(values, "name");
	}

	private TypeElement getTypeFrom(Map<? extends ExecutableElement, ? extends AnnotationValue> values) {
		return (TypeElement) ((DeclaredType) getValue(values, "delegate")).asElement();
	}

	private Object getValue(Map<? extends ExecutableElement, ? extends AnnotationValue> values, String name) {
		for (final var entry : values.entrySet()) {
			if (entry.getKey().getSimpleName().toString().equals(name)) {
				return entry.getValue().getValue();
			}
		}
		throw new IllegalStateException();
	}

	private void generate(TypeElement typeElement) throws IOException {
		final var specification = getSpecificationOf(typeElement);
		final var forwarderType = specification.forwarderType();
		final var delegateType = specification.delegateType();
		final var delegateTypeName = ClassName.get(delegateType);
		final var typeBuilder = TypeSpec
			.classBuilder(forwarderType)
			.addModifiers(PUBLIC)
			.addOriginatingElement(typeElement)
			.superclass(ParameterizedTypeName.get(ABSTRACT_LISTENABLE_PROPERTIES, delegateTypeName))
			.addSuperinterface(delegateTypeName)
			.addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", getClass().getName()).build())
			.addMethod(specification.buildConstructor());
		int index = 0;
		final var areFieldsEqualBuilder = CodeBlock.builder().beginControlFlow("return switch (indexOfField)");
		for (final var element : getElementUtils().getAllMembers(delegateType)) {
			if (element instanceof final ExecutableElement method) {
				final var solution = chooseSolutionFor(delegateType, method);
				if (solution == DO_NOT_FORWARD) {
					continue;
				}
				final var methodName = method.getSimpleName();
				final var methodBuilder = MethodSpec.overriding(method).addModifiers(FINAL);
				switch (solution) {
					case FORWARD_WITHOUT_MARKING_ANY_FIELDS_AS_ACCESSED: {
						final var statement = new StringBuilder();
						if (method.getReturnType().getKind() != VOID) {
							statement.append("return ");
						}
						statement.append("getDelegateWithoutRecordingAccess().$N(");
						for (final var iterator = method.getParameters().iterator(); iterator.hasNext();) {
							statement.append(iterator.next().getSimpleName());
							if (iterator.hasNext()) {
								statement.append(", ");
							}
						}
						statement.append(")");
						methodBuilder.addStatement(statement.toString(), methodName);
						break;
					}
					case FORWARD_AFTER_MARKING_FIELD_AS_ACCESSED: {
						final var indexOfField = index ++;
						if (method.getReturnType().getKind().isPrimitive()) {
							areFieldsEqualBuilder
								.addStatement("case $L -> newDelegate.$N() == oldDelegate.$N()", indexOfField, methodName, methodName);
						} else {
							areFieldsEqualBuilder
								.addStatement(
									"case $L -> $T.equals(newDelegate.$N(), oldDelegate.$N())",
									indexOfField,
									Objects.class,
									methodName,
									methodName
								);
						}
						methodBuilder.addStatement("return recordAccessToField($L).$N()", indexOfField, methodName);
						break;
					}
					case FORWARD_AFTER_MARKING_EVERY_FIELD_AS_ACCESSED: {
						final var statement = new StringBuilder();
						if (method.getReturnType().getKind() != VOID) {
							statement.append("return ");
						}
						statement.append("recordAccessToEveryField().$N(");
						for (final var iterator = method.getParameters().iterator(); iterator.hasNext();) {
							statement.append(iterator.next().getSimpleName());
							if (iterator.hasNext()) {
								statement.append(", ");
							}
						}
						statement.append(")");
						methodBuilder.addStatement(statement.toString(), methodName);
						break;
					}
					default:
						continue;
				}
				final var returnType = method.getReturnType();
				if (returnType.getKind() == TYPEVAR) {
					error(
						"Methods that return a type variable are not yet supported by @GenerateSubstitutableForwarder!",
						typeElement
					);

				} else {
					typeBuilder.addMethod(methodBuilder.build());
				}
			}
		}
		areFieldsEqualBuilder.addStatement("default -> throw new $T()", IndexOutOfBoundsException.class).endControlFlow("");
		typeBuilder
			.addMethod(
				methodBuilder("getNumberOfFields")
					.addAnnotation(Override.class)
					.addModifiers(PROTECTED, FINAL)
					.returns(INT)
					.addStatement("return $L", index)
					.build()
			);
		typeBuilder
			.addMethod(
				methodBuilder("areFieldsEqual")
					.addAnnotation(Override.class)
					.addModifiers(PROTECTED, FINAL)
					.addParameter(delegateTypeName, "oldDelegate")
					.addParameter(delegateTypeName, "newDelegate")
					.addParameter(INT, "indexOfField")
					.returns(BOOLEAN)
					.addCode(areFieldsEqualBuilder.build())
					.build()
			);
		final var javaFile = JavaFile
			.builder(forwarderType.packageName(), typeBuilder.build())
			.skipJavaLangImports(true)
			.indent("\t")
			.build();
		javaFile.toJavaFileObject().delete();
		javaFile.writeTo(processingEnv.getFiler());
	}

	private ForwardStrategy chooseSolutionFor(TypeElement classElement, ExecutableElement executableElement) {
		final var modifiers = executableElement.getModifiers();
		if (modifiers.contains(STATIC)) {
			return DO_NOT_FORWARD;
		}
		if (modifiers.contains(PRIVATE)) {
			return DO_NOT_FORWARD;
		}
		if (modifiers.contains(FINAL)) {
			return DO_NOT_FORWARD;
		}
		if (modifiers.contains(NATIVE)) {
			return DO_NOT_FORWARD;
		}
		final var annotatedStrategy = getValueOfForwardAnnotation(classElement, executableElement);
		if (annotatedStrategy != null) {
			return annotatedStrategy;
		}
		final var name = executableElement.getSimpleName();
		if (name.contentEquals("equals") || name.contentEquals("toString")) {
			return DO_NOT_FORWARD;
		}
		final var hasParameters = executableElement.getParameters().size() > 0;
		final var isVoid = executableElement.getReturnType().getKind() == VOID;
		final var isDefault = modifiers.contains(DEFAULT);
		if (hasParameters || isVoid) {
			if (isDefault) {
				return DO_NOT_FORWARD;
			} else {
				return FORWARD_AFTER_MARKING_EVERY_FIELD_AS_ACCESSED;
			}
		}
		for (final var annotation : executableElement.getAnnotationMirrors()) {
			final var annotationName = annotation.getAnnotationType().asElement().toString();
			switch (annotationName) {
				case IMMUTABLES_DEFAULT_ANNOTATION, IMMUTABLES_DERIVED_ANNOTATION, IMMUTABLES_LAZY_ANNOTATION -> {
					return FORWARD_AFTER_MARKING_FIELD_AS_ACCESSED;
				}
			}
		}
		if (isDefault) {
			return DO_NOT_FORWARD;
		}
		return FORWARD_AFTER_MARKING_FIELD_AS_ACCESSED;
	}

	public ForwardStrategy getValueOfForwardAnnotation(TypeMirror classElement, ExecutableElement targetMethod) {
		if (classElement.getKind() == NONE) {
			return null;
		}
		final var elementUtils = getElementUtils();
		return getValueOfForwardAnnotation(elementUtils.getTypeElement(classElement.toString()), targetMethod);
	}

	public ForwardStrategy getValueOfForwardAnnotation(TypeElement classElement, ExecutableElement overrider) {
		final var utils = getElementUtils();
		for (final var element : classElement.getEnclosedElements()) {
			if (element instanceof final ExecutableElement overridden) {
				if (overridden.equals(overrider) || utils.overrides(overrider, overridden, classElement)) {
					for (final var annotation : overridden.getAnnotationMirrors()) {
						if (annotation.getAnnotationType().asElement().toString().equals(FORWARD_ANNOTATION)) {
							for (final var annotationValue : annotation.getElementValues().values()) {
								if (annotationValue.getValue()instanceof final VariableElement variableElement) {
									final var simpleName = variableElement.getSimpleName().toString();
									return valueOf(simpleName);
								}
							}
						}
					}
				}
			}
		}
		for (final var interfaceMirror : classElement.getInterfaces()) {
			final var overridden = getValueOfForwardAnnotation(interfaceMirror, overrider);
			if (overridden != null) {
				return overridden;
			}
		}
		return getValueOfForwardAnnotation(classElement.getSuperclass(), overrider);
	}

	private Elements getElementUtils() {
		return processingEnv.getElementUtils();
	}

	private void error(String message, Element element) {
		final var annotation = getGenerateSubstitutableForwarderAnnotationOf(element);
		processingEnv.getMessager().printMessage(ERROR, message, element, annotation);
	}

	private AnnotationMirror getGenerateSubstitutableForwarderAnnotationOf(Element element) {
		if (element == null) {
			return null;
		}
		final var type = getGenerateSubstitutableForwarderAnnotationTypeElement();
		for (final var annotation : element.getAnnotationMirrors()) {
			if (annotation.getAnnotationType().asElement().equals(type)) {
				return annotation;
			}
		}
		return null;
	}

	private TypeElement getGenerateSubstitutableForwarderAnnotationTypeElement() {
		return getElementUtils().getTypeElement(GenerateSubstitutableForwarder.class.getName());
	}

}