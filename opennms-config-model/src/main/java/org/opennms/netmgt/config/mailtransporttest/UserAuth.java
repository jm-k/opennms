/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.config.mailtransporttest;

  import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Configure user based authentication.
 */

@XmlRootElement(name="user-auth")
@XmlAccessorType(XmlAccessType.FIELD)
public class UserAuth implements Serializable {
    private static final long serialVersionUID = 2159704058514250131L;

    /**
     * Field m_userName.
     */
    @XmlAttribute(name="user-name")
    private String m_userName;

    /**
     * Field m_password.
     */
    @XmlAttribute(name="password")
    private String m_password;


      //----------------/
     //- Constructors -/
    //----------------/

    public UserAuth() {
        super();
    }


      //-----------/
     //- Methods -/
    //-----------/

    public UserAuth(final String username, final String password) {
        super();
        m_userName = username;
        m_password = password;
    }


    /**
     * Overrides the Object.equals method.
     * 
     * @param obj
     * @return true if the objects are equal.
     */
    @Override()
    public boolean equals(final Object obj) {
        if ( this == obj ) return true;
        
        if (obj instanceof UserAuth) {
            final UserAuth temp = (UserAuth)obj;
            if (m_userName != null) {
                if (temp.m_userName == null) {
                    return false;
                } else if (!(m_userName.equals(temp.m_userName))) {
                    return false;
                }
            } else if (temp.m_userName != null) {
                return false;
            }
            if (m_password != null) {
                if (temp.m_password == null) {
                    return false;
                } else if (!(m_password.equals(temp.m_password))) {
                    return false;
                }
            } else if (temp.m_password != null) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the value of field 'password'.
     * 
     * @return the value of field 'Password'.
     */
    public String getPassword() {
        return m_password == null? "opennms" : m_password;
    }

    /**
     * Returns the value of field 'userName'.
     * 
     * @return the value of field 'UserName'.
     */
    public String getUserName() {
        return m_userName == null? "opennms" : m_userName;
    }

    /**
     * Overrides the Object.hashCode method.
     * <p>
     * The following steps came from <b>Effective Java Programming
     * Language Guide</b> by Joshua Bloch, Chapter 3
     * 
     * @return a hash code value for the object.
     */
    public int hashCode() {
        int result = 17;
        
        if (m_userName != null) {
           result = 37 * result + m_userName.hashCode();
        }
        if (m_password != null) {
           result = 37 * result + m_password.hashCode();
        }
        
        return result;
    }

    /**
     * Sets the value of field 'password'.
     * 
     * @param password the value of field 'password'.
     */
    public void setPassword(final String password) {
        m_password = password;
    }

    /**
     * Sets the value of field 'userName'.
     * 
     * @param userName the value of field 'userName'.
     */
    public void setUserName(final String userName) {
        m_userName = userName;
    }

}
