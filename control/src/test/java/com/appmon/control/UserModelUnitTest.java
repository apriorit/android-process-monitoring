package com.appmon.control;

import com.appmon.control.models.user.IPreferences;
import com.appmon.control.models.user.IUserModel;
import com.appmon.control.models.user.UserModel;
import com.appmon.shared.DatabaseError;
import com.appmon.shared.IAuthService;
import com.appmon.shared.ICloudServices;
import com.appmon.shared.IDatabaseService;
import com.appmon.shared.IUser;
import com.appmon.shared.ResultListener;
import com.appmon.shared.exceptions.AuthEmailTakenException;
import com.appmon.shared.exceptions.AuthException;
import com.appmon.shared.exceptions.AuthInvalidEmailException;
import com.appmon.shared.exceptions.AuthWeakPasswordException;
import com.appmon.shared.exceptions.AuthWrongPasswordException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;
import static org.junit.Assume.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class UserModelUnitTest {

    @Mock
    private IDatabaseService mockedDb;

    @Mock
    private IAuthService mockedAuth;

    @Mock
    private ICloudServices mockedCloud;

    @Mock
    private IPreferences mockedPref;

    @Mock
    private IUser user;

    @Captor
    private ArgumentCaptor<ResultListener<Void, DatabaseError>> dbResult;

    @Captor
    private ArgumentCaptor<ResultListener<Void, IUser.ChangePasswordError>>
            changePasswordResult;

    @Captor
    private ArgumentCaptor<ResultListener<Void, Throwable>> throwableResult;

    @Captor
    private ArgumentCaptor<ResultListener<IUser, Throwable>> throwableUserResult;

    private UserModel model;

    @Before
    public void setup() {
        when(mockedCloud.getAuth()).thenReturn(mockedAuth);
        when(mockedCloud.getDatabase()).thenReturn(mockedDb);
        when(mockedAuth.getUser()).thenReturn(user);
        model = new UserModel(mockedCloud, mockedPref);
    }

    @Test
    public void loginTest() {
        IUserModel.ISignInListener l = mock(IUserModel.ISignInListener.class);
        model.addSignInListener(l);
        // early email check
        model.signInWithEmail("wrong_email", "something");
        verify(l).onFail(IUserModel.SignInError.INVALID_EMAIL);
        reset(l);
        // early password check
        model.signInWithEmail("cool@email.com", "1");
        verify(l).onFail(IUserModel.SignInError.WRONG_PASSWORD);
        // wrong email
        model.signInWithEmail("email@email.com", "coolpass");
        verify(mockedAuth).signInWithEmail(any(String.class), any(String.class),
                throwableUserResult.capture());
        throwableUserResult.getValue().onFailure(new AuthInvalidEmailException(null));
        verify(l).onFail(IUserModel.SignInError.INVALID_EMAIL);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthWrongPasswordException(null));
        verify(l).onFail(IUserModel.SignInError.WRONG_PASSWORD);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthWrongPasswordException(null));
        verify(l).onFail(IUserModel.SignInError.WRONG_PASSWORD);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthException(null));
        throwableUserResult.getValue().onFailure(new Throwable());
        verify(l, times(2)).onFail(IUserModel.SignInError.INTERNAL_ERROR);
        reset(l);
        // success
        throwableUserResult.getValue().onSuccess(user);
        verify(l).onSuccess();
        model.removeSignInListener(l);
    }

    @Test
    public void registerTest() {
        IUserModel.IRegisterListener l = mock(IUserModel.IRegisterListener.class);
        model.addRegisterListener(l);
        // early email check
        model.registerWithEmail("wrong_email", "something");
        verify(l).onFail(IUserModel.RegisterError.INVALID_EMAIL);
        reset(l);
        // early password check
        model.registerWithEmail("cool@email.com", "1");
        verify(l).onFail(IUserModel.RegisterError.WEAK_PASSWORD);
        // wrong email
        model.registerWithEmail("email@email.com", "coolpass");
        verify(mockedAuth).registerWithEmail(any(String.class), any(String.class),
                throwableUserResult.capture());
        throwableUserResult.getValue().onFailure(new AuthEmailTakenException(null));
        verify(l).onFail(IUserModel.RegisterError.USER_EXISTS);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthInvalidEmailException(null));
        verify(l).onFail(IUserModel.RegisterError.INVALID_EMAIL);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthWeakPasswordException(null));
        verify(l).onFail(IUserModel.RegisterError.WEAK_PASSWORD);
        reset(l);
        throwableUserResult.getValue().onFailure(new AuthException(null));
        throwableUserResult.getValue().onFailure(new Throwable());
        verify(l, times(2)).onFail(IUserModel.RegisterError.INTERNAL_ERROR);
        reset(l);
        // success
        throwableUserResult.getValue().onSuccess(user);
        verify(l).onSuccess();
        model.removeRegisterListener(l);
    }

    @Test
    public void userIdTest() {
        when(user.getUserID()).thenReturn("test");
        when(mockedAuth.getUser()).thenReturn(null, user);
        UserModel testModel = new UserModel(mockedCloud, mockedPref);
        assertNull(testModel.getUserID());
        testModel = new UserModel(mockedCloud, mockedPref);
        assertNotNull(testModel.getUserID());
    }
    @Test
    public void appPinTest() {
        when(mockedPref.getString("app_pin")).thenReturn("123123");
        assumeTrue(model.getAppPin().equals("123123"));
        IUserModel.IChangeAppPinListener l = mock(IUserModel.IChangeAppPinListener.class);
        model.addChangeAppPinListener(l);
        model.changeAppPin("333333");
        verify(mockedPref).setString("app_pin", "333333");
        verify(l).onSuccess();
        model.changeAppPin("");
        verify(mockedPref).remove("app_pin");
        model.changeAppPin("1");
        verify(l).onFail(IUserModel.ChangeAppPinError.WEAK_PIN);
        model.removeChangeAppPinListener(l);
    }

    @Test
    public void ChangeClientPinTest() {
        IUserModel.IChangeClientPinListener l = mock(IUserModel.IChangeClientPinListener.class);
        model.addChangeClientPinListener(l);
        model.changeClientPin("1");
        verify(l).onFail(IUserModel.ChangeClientPinError.WEAK_PIN);
        model.changeClientPin("123123");
        verify(mockedDb).setValue(any(String.class), any(String.class), dbResult.capture());
        dbResult.getValue().onFailure(DatabaseError.NETWORK_ERROR);
        verify(l).onFail(IUserModel.ChangeClientPinError.INTERNAL_ERROR);
        dbResult.getValue().onSuccess(null);
        verify(l).onSuccess();
        model.removeChangeClientPinListener(l);
    }

    @Test
    public void changePasswordTest() {
        IUserModel.IChangePasswordListener l = mock(IUserModel.IChangePasswordListener.class);
        model.addChangePasswordListener(l);
        model.changePassword("1");
        verify(l).onFail(IUserModel.ChangePasswordError.WEAK_PASSWORD);
        model.changePassword("goodPasss");
        verify(user).changePassword(anyString(), changePasswordResult.capture());
        changePasswordResult.getValue().onFailure(IUser.ChangePasswordError.INTERNAL_ERROR);
        verify(l).onFail(IUserModel.ChangePasswordError.INTERNAL_ERROR);
        reset(l);
        changePasswordResult.getValue().onFailure(IUser.ChangePasswordError.REAUTH_NEEDED);
        verify(l).onFail(IUserModel.ChangePasswordError.REAUTH_NEEDED);
        reset(l);
        changePasswordResult.getValue().onFailure(IUser.ChangePasswordError.WEAK_PASSWORD);
        verify(l).onFail(IUserModel.ChangePasswordError.WEAK_PASSWORD);
        changePasswordResult.getValue().onSuccess(null);
        verify(l).onSuccess();
        model.removeChangePasswordListener(l);
    }

    @Test
    public void resetPasswordTest() {
        IUserModel.IResetPasswordListener l = mock(IUserModel.IResetPasswordListener.class);
        model.addResetPasswordListener(l);
        model.resetPassword("1");
        verify(l).onFail(IUserModel.ResetPasswordError.INVALID_USER);
        model.resetPassword("qweqwe@qweqwe.qw");
        verify(mockedAuth).resetPassword(anyString(), throwableResult.capture());
        throwableResult.getValue().onFailure(new Exception());
        verify(l).onFail(IUserModel.ResetPasswordError.INTERNAL_ERROR);
        throwableResult.getValue().onSuccess(null);
        verify(l).onSuccess();
        model.removeResetPasswordListener(l);
    }

    @Test
    public void signOutTest() {
        model.signOut();
        verify(mockedAuth).signOut();
    }

}
