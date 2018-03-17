/*
 * This file is part of React, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2013 Flow Powered <https://flowpowered.com/>
 * Original ReactPhysics3D C++ library by Daniel Chappuis <http://danielchappuis.ch>
 * React is re-licensed with permission from ReactPhysics3D author.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.flowpowered.react.collision.shape;

import java.util.ArrayList;
import java.util.List;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import com.flowpowered.react.ReactDefaults;
import com.flowpowered.react.math.Matrix3x3;
import com.flowpowered.react.math.Vector3;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/**
 * This class represents a convex mesh shape. In order to create a convex mesh shape, you need to indicate the local-space position of the mesh vertices. You do this either by passing a vertices array
 * to the constructor or using the addVertex() method. Make sure that the set of vertices that you use to create the shape are indeed part of a convex mesh. The center of mass of the shape will be at
 * the origin of the local-space geometry that you use to create the mesh. The method used for collision detection with a convex mesh shape has an O(n) running time with "n" being the number of
 * vertices in the mesh. Therefore, you should try not to use too many vertices. However, it is possible to speed up the collision detection by using the edges information of your mesh. The running
 * time of the collision detection that uses the edges is almost O(1) constant time at the cost of additional memory used to store the vertices. You can indicate edges information with the addEdge()
 * method. Then, you must use the setIsEdgesInformationUsed(true) method in order to use the edges information for collision detection.
 */
public class ConvexMeshShape extends CollisionShape {
    private final List<Vector3> mVertices = new ArrayList<>();
    private int mNbVertices;
    private final Vector3 mMinBounds;
    private final Vector3 mMaxBounds;
    private boolean mIsEdgesInformationUsed;
    private final TIntObjectMap<TIntSet> mEdgesAdjacencyList = new TIntObjectHashMap<>();
    private int mCachedSupportVertex;

    /**
     * Constructs a new convex mesh shape from the array of vertices, the number of vertices in the mesh and the stride in bytes (the amount of bytes per vertex)
     *
     * @param arrayVertices The array of vertices
     * @param nbVertices The number of vertices in the mesh
     * @param stride The vertex stride in bytes
     */
    public ConvexMeshShape(float[] arrayVertices, int nbVertices, int stride) {
        this(arrayVertices, nbVertices, stride, ReactDefaults.OBJECT_MARGIN);
    }

    /**
     * Constructs a new convex mesh shape from the array of vertices, the number of vertices in the mesh, the stride in bytes (the amount of bytes per vertex) and the collision margin.
     *
     * @param arrayVertices The array of vertices
     * @param nbVertices The number of vertices in the mesh
     * @param stride The vertex stride in bytes
     * @param margin The collision margin
     */
    public ConvexMeshShape(float[] arrayVertices, int nbVertices, int stride, float margin) {
        super(CollisionShapeType.CONVEX_MESH, margin);
        mNbVertices = nbVertices;
        mMinBounds = new Vector3(0, 0, 0);
        mMaxBounds = new Vector3(0, 0, 0);
        mIsEdgesInformationUsed = false;
        mCachedSupportVertex = 0;
        if (nbVertices <= 0) {
            throw new IllegalArgumentException("Number of vertices must be greater than zero");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("Stride must be greater than zero");
        }
        if (margin <= 0) {
            throw new IllegalArgumentException("Margin must be greater than 0");
        }
        int vertexPointer = 0;
        for (int i = 0; i < mNbVertices; i++) {
            final int newPoint = vertexPointer / 4;
            mVertices.add(new Vector3(arrayVertices[newPoint], arrayVertices[newPoint + 1], arrayVertices[newPoint + 2]));
            vertexPointer += stride;
        }
        recalculateBounds();
    }

    /**
     * Constructs a new convex mesh shape with no mesh.
     */
    public ConvexMeshShape() {
        this(ReactDefaults.OBJECT_MARGIN);
    }

    /**
     * Constructs a new convex mesh shape with no mesh from the collision margin.
     *
     * @param margin The collision margin
     */
    public ConvexMeshShape(float margin) {
        super(CollisionShapeType.CONVEX_MESH, margin);
        mNbVertices = 0;
        mMinBounds = new Vector3(0, 0, 0);
        mMaxBounds = new Vector3(0, 0, 0);
        mIsEdgesInformationUsed = false;
        mCachedSupportVertex = 0;
        if (margin <= 0) {
            throw new IllegalArgumentException("Margin must be greater than 0");
        }
    }

    /**
     * Copy constructor.
     *
     * @param shape The shape to copy
     */
    public ConvexMeshShape(ConvexMeshShape shape) {
        super(shape);
        mVertices.addAll(shape.mVertices);
        mNbVertices = shape.mNbVertices;
        mMinBounds = new Vector3(shape.mMinBounds);
        mMaxBounds = new Vector3(shape.mMaxBounds);
        mIsEdgesInformationUsed = shape.mIsEdgesInformationUsed;
        mEdgesAdjacencyList.putAll(shape.mEdgesAdjacencyList);
        mCachedSupportVertex = shape.mCachedSupportVertex;
        if (mNbVertices != mVertices.size()) {
            throw new IllegalArgumentException("The number of vertices must be equal to the size of the vertex list");
        }
    }

    // Recomputes the bounds of the mesh.
    private void recalculateBounds(@UnderInitialization ConvexMeshShape this) {
        mMinBounds.setToZero();
        mMaxBounds.setToZero();
        for (int i = 0; i < mNbVertices; i++) {
            if (mVertices.get(i).getX() > mMaxBounds.getX()) {
                mMaxBounds.setX(mVertices.get(i).getX());
            }
            if (mVertices.get(i).getX() < mMinBounds.getX()) {
                mMinBounds.setX(mVertices.get(i).getX());
            }
            if (mVertices.get(i).getY() > mMaxBounds.getY()) {
                mMaxBounds.setY(mVertices.get(i).getY());
            }
            if (mVertices.get(i).getY() < mMinBounds.getY()) {
                mMinBounds.setY(mVertices.get(i).getY());
            }
            if (mVertices.get(i).getZ() > mMaxBounds.getZ()) {
                mMaxBounds.setZ(mVertices.get(i).getZ());
            }
            if (mVertices.get(i).getZ() < mMinBounds.getZ()) {
                mMinBounds.setZ(mVertices.get(i).getZ());
            }
        }
        mMaxBounds.add(new Vector3(mMargin, mMargin, mMargin));
        mMinBounds.subtract(new Vector3(mMargin, mMargin, mMargin));
    }

    /**
     * Adds a vertex into the convex mesh.
     *
     * @param vertex The vertex to add
     */
    public void addVertex(Vector3 vertex) {
        mVertices.add(vertex);
        mNbVertices++;
        if (vertex.getX() > mMaxBounds.getX()) {
            mMaxBounds.setX(vertex.getX());
        }
        if (vertex.getX() < mMinBounds.getX()) {
            mMinBounds.setX(vertex.getX());
        }
        if (vertex.getY() > mMaxBounds.getY()) {
            mMaxBounds.setY(vertex.getY());
        }
        if (vertex.getY() < mMinBounds.getY()) {
            mMinBounds.setY(vertex.getY());
        }
        if (vertex.getZ() > mMaxBounds.getZ()) {
            mMaxBounds.setZ(vertex.getZ());
        }
        if (vertex.getZ() < mMinBounds.getZ()) {
            mMinBounds.setZ(vertex.getZ());
        }
    }

    /**
     * Adds an edge into the convex mesh by specifying the two vertex indices of the edge. Note that the vertex indices start at zero and need to correspond to the order of the vertices in the vertex
     * array in the constructor or the order of the calls of the addVertex() methods that were used to add vertices into the convex mesh.
     *
     * @param v1 The first vertex of the edge
     * @param v2 The second vertex of the edge
     */
    public void addEdge(int v1, int v2) {
        if (v1 < 0) {
            throw new IllegalArgumentException("v1 must be greater or equal to zero");
        }
        if (v2 < 0) {
            throw new IllegalArgumentException("v2 must be greater or equal to zero");
        }
        if (!mEdgesAdjacencyList.containsKey(v1)) {
            mEdgesAdjacencyList.put(v1, new TIntHashSet());
        }
        if (!mEdgesAdjacencyList.containsKey(v2)) {
            mEdgesAdjacencyList.put(v2, new TIntHashSet());
        }
        mEdgesAdjacencyList.get(v1).add(v2);
        mEdgesAdjacencyList.get(v2).add(v1);
    }

    /**
     * Returns true if the edges information is used to speed up the collision detection.
     *
     * @return Whether or not the edge information is used
     */
    public boolean isEdgesInformationUsed() {
        return mIsEdgesInformationUsed;
    }

    /**
     * Sets the variable to know if the edges information is used to speed up the collision detection.
     *
     * @param isEdgesUsed Whether or not to use the edge information
     */
    public void setIsEdgesInformationUsed(boolean isEdgesUsed) {
        mIsEdgesInformationUsed = isEdgesUsed;
    }

    @Override
    public Vector3 getLocalSupportPointWithMargin(Vector3 direction) {
        final Vector3 supportPoint = getLocalSupportPointWithoutMargin(direction);
        final Vector3 unitDirection = new Vector3(direction);
        if (direction.lengthSquare() < ReactDefaults.MACHINE_EPSILON * ReactDefaults.MACHINE_EPSILON) {
            unitDirection.setAllValues(1, 1, 1);
        }
        unitDirection.normalize();
        return Vector3.add(supportPoint, Vector3.multiply(unitDirection, mMargin));
    }

    @Override
    public Vector3 getLocalSupportPointWithoutMargin(Vector3 direction) {
        if (mNbVertices != mVertices.size()) {
            throw new IllegalArgumentException("The number of vertices must be equal to the size of the vertex list");
        }
        if (mIsEdgesInformationUsed) {
            if (mEdgesAdjacencyList.size() != mNbVertices) {
                throw new IllegalStateException("The number of adjacent edge lists must be equal to the number of vertices");
            }
            int maxVertex = mCachedSupportVertex;
            float maxDotProduct = direction.dot(mVertices.get(maxVertex));
            boolean isOptimal;
            do {
                isOptimal = true;
                final TIntSet edgeSet = mEdgesAdjacencyList.get(maxVertex);
                if (edgeSet.size() <= 0) {
                    throw new IllegalStateException("The number of adjacent edges must be greater than zero");
                }
                final TIntIterator it = edgeSet.iterator();
                while (it.hasNext()) {
                    final int i = it.next();
                    final float dotProduct = direction.dot(mVertices.get(i));
                    if (dotProduct > maxDotProduct) {
                        maxVertex = i;
                        maxDotProduct = dotProduct;
                        isOptimal = false;
                    }
                }
            } while (!isOptimal);
            mCachedSupportVertex = maxVertex;
            return mVertices.get(maxVertex);
        } else {
            float maxDotProduct = -Float.MAX_VALUE;
            int indexMaxDotProduct = 0;
            for (int i = 0; i < mNbVertices; i++) {
                final float dotProduct = direction.dot(mVertices.get(i));
                if (dotProduct > maxDotProduct) {
                    indexMaxDotProduct = i;
                    maxDotProduct = dotProduct;
                }
            }
            if (maxDotProduct < 0) {
                throw new IllegalStateException("Max dot product is not greater or equal to zero");
            }
            return mVertices.get(indexMaxDotProduct);
        }
    }

    @Override
    public void getLocalBounds(Vector3 min, Vector3 max) {
        min.set(mMinBounds);
        max.set(mMaxBounds);
    }

    @Override
    public void computeLocalInertiaTensor(Matrix3x3 tensor, float mass) {
        final float factor = (1f / 3) * mass;
        final Vector3 realExtent = Vector3.multiply(0.5f, Vector3.subtract(mMaxBounds, mMinBounds));
        if (realExtent.getX() <= 0 || realExtent.getY() <= 0 || realExtent.getZ() <= 0) {
            throw new IllegalStateException("Real extent components must all be greater than zero");
        }
        final float xSquare = realExtent.getX() * realExtent.getX();
        final float ySquare = realExtent.getY() * realExtent.getY();
        final float zSquare = realExtent.getZ() * realExtent.getZ();
        tensor.setAllValues(
                factor * (ySquare + zSquare), 0, 0,
                0, factor * (xSquare + zSquare), 0,
                0, 0, factor * (xSquare + ySquare));
    }

    @Override
    public CollisionShape clone() {
        return new ConvexMeshShape(this);
    }

    @Override
    public boolean isEqualTo(CollisionShape otherCollisionShape) {
        final ConvexMeshShape otherShape = (ConvexMeshShape) otherCollisionShape;
        return mNbVertices == otherShape.mNbVertices && !mIsEdgesInformationUsed && mVertices.equals(otherShape.mVertices) && mEdgesAdjacencyList.equals(otherShape.mVertices);
    }
}
