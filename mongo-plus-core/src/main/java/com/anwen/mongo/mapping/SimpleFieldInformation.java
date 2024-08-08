package com.anwen.mongo.mapping;

import com.anwen.mongo.annotation.ID;
import com.anwen.mongo.annotation.collection.CollectionField;
import com.anwen.mongo.cache.global.FieldCache;
import com.anwen.mongo.cache.global.PropertyCache;
import com.anwen.mongo.cache.global.SimpleCache;
import com.anwen.mongo.constant.SqlOperationConstant;
import com.anwen.mongo.domain.MongoPlusFieldException;
import com.anwen.mongo.toolkit.ArrayUtils;
import com.anwen.mongo.toolkit.ClassTypeUtil;
import com.anwen.mongo.toolkit.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * @author JiaChaoYang
 **/
public class SimpleFieldInformation<T> implements FieldInformation {


    private Object value;

    private String name;

    private Class<?> mapValueType;

    private Class<?> collectionValueType = Object.class;

    private final Field field;

    private ID id;

    private CollectionField collectionField;

    private Method get;

    private Method set;

    private Type[] types;

    private String camelCaseName;

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public Class<?> getTypeClass() {
        return typeClass;
    }

    public Type[] getType() {
        if (ArrayUtils.isEmpty(types)) {
            try {
                types = ((ParameterizedType) getGenericType()).getActualTypeArguments();
            }catch (Exception ignored){
            }
        }
        return this.types;
    }

    @Override
    public TypeInformation getTypeInformation() {
        return new SimpleTypeInformation<>(getTypeClass(),getType());
    }

    private final Class<?> typeClass;

    private final T instance;

    public SimpleFieldInformation(T instance, Field field) {
        this.instance = instance;
        field.setAccessible(true);
        this.field = field;
        this.typeClass = field.getType();
    }

    @Override
    public Object getValue() {
        if (this.value == null){
            try {
                this.value = field.get(instance);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return this.value;
    }

    @Override
    public String getName() {
        if (this.name == null){
            this.name = field.getName();
            if (getCollectionField() != null && StringUtils.isNotBlank(getCollectionField().value())){
                this.name = getCollectionField().value();
            }
        }
        return this.name;
    }

    @Override
    public String getCamelCaseName() {
        if (this.camelCaseName == null) {
            String fieldName = getName();
            if (PropertyCache.camelToUnderline){
                if (getCollectionField() == null || StringUtils.isBlank(getCollectionField().value())){
                    fieldName = StringUtils.camelToUnderline(fieldName);
                }
            }
            this.camelCaseName = fieldName;
        }
        return this.camelCaseName;
    }

    @Override
    public String getIdOrCamelCaseName() {
        return isId() ? SqlOperationConstant._ID : getCamelCaseName();
    }

    @Override
    public boolean isMap(){
        return ClassTypeUtil.isTargetClass(Map.class,typeClass);
    }

    @Override
    public Class<?> mapValueType(){
        if (isMap() && this.mapValueType == null) {
            Type[] typeArguments = ((ParameterizedType) getGenericType()).getActualTypeArguments();
            this.mapValueType = (Class<?>) typeArguments[1];
        }
        return this.mapValueType;
    }

    @Override
    public Class<?> collectionValueType() {
        if (isCollection() && this.collectionValueType == null){
            Type genericType = getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                    this.collectionValueType = (Class<?>) actualTypeArguments[0];
                }
            }
        }
        return this.collectionValueType;
    }

    @Override
    public boolean isCollection(){
        return typeClass.isArray() //
                || Iterable.class.equals(typeClass) //
                || ClassTypeUtil.isTargetClass(Collection.class,typeClass);
    }

    @Override
    public boolean isSimpleType(){
        return SimpleCache.getSimpleTypeHolder().isSimpleType(typeClass);
    }

    @Override
    public boolean isSkipCheckField() {
        return getCollectionField() != null && !getCollectionField().exist();
    }

    @Override
    public boolean isSkipCheckFieldAndId() {
        return isSkipCheckField() || isId();
    }

    @Override
    public boolean isId() {
        return getId() != null;
    }

    @Override
    public ID getId() {
        if (this.id == null){
            this.id = field.getAnnotation(ID.class);
        }
        return this.id;
    }

    @Override
    public Method getMethod() {
        try {
            if (get == null) {
                get = instance.getClass().getMethod(capitalize("get", field.getName()), typeClass);
            }
        } catch (NoSuchMethodException e) {
            throw new MongoPlusFieldException("The get method to obtain the " + field.getName() +" field failed",e);
        }
        return get;
    }

    @Override
    public Method setMethod() {
        try {
            if (set == null) {
                set = instance.getClass().getMethod(capitalize("set", field.getName()), typeClass);
            }
        } catch (NoSuchMethodException e) {
            throw new MongoPlusFieldException("The set method to obtain the " + field.getName() +" field failed",e);
        }
        return set;
    }

    @Override
    public void setValue(Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new MongoPlusFieldException("Failed to set the " + field.getName()+" field content",e);
        }
    }

    private String capitalize(String method,String str) {
        return method+(str.substring(0, 1).toUpperCase() + str.substring(1));
    }

    @Override
    public CollectionField getCollectionField() {
        if (FieldCache.getCollectionField(field) == null){
            CollectionField collectionField = field.getAnnotation(CollectionField.class);
            if (collectionField != null) {
                FieldCache.setCollectionFieldMapCache(field,collectionField);
            }
        }
        return FieldCache.getCollectionField(field);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass){
        return getField().getAnnotation(annotationClass);
    }

    @Override
    public boolean isAnnotation(Class<? extends Annotation> annotationClass) {
        return getField().isAnnotationPresent(annotationClass);
    }

    @Override
    public Type getGenericType() {
        if (FieldCache.getGenericType(field) == null){
            FieldCache.setGenericTypeMapCache(field,field.getGenericType());
        }
        return FieldCache.getGenericType(field);
    }

}
