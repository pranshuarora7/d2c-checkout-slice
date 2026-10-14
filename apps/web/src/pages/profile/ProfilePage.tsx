import { useProfile } from '../../context/ProfileContext';
import { ErrorNotice } from '../../components/ErrorNotice';
import { Loading } from '../../components/Loading';
import { PageHeading } from '../../components/PageHeading';
import { AddressBook } from '../../components/profile/AddressBook';
import { ProfileEditor } from '../../components/profile/ProfileEditor';

export function ProfilePage() {
  const profile = useProfile();
  return (
    <>
      <PageHeading eyebrow="YOUR ACCOUNT" title="Your details.">
        Keep your contact details and delivery addresses up to date.
      </PageHeading>
      <ErrorNotice error={profile.error} retry={profile.refresh} />
      {profile.loading && !profile.data && <Loading />}
      {profile.data && (
        <div className="account-layout">
          <ProfileEditor />
          <AddressBook />
        </div>
      )}
    </>
  );
}
