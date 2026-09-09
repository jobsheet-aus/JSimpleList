alter table jsimplelist.profiles
    drop constraint if exists profiles_avatar_icon_check;

alter table jsimplelist.profiles
    add constraint profiles_avatar_icon_check
    check (
        avatar_icon in (
            'person',
            'flower',
            'cat',
            'horse',
            'lightning',
            'coffee',
            'helmet',
            'paw',
            'book',
            'alien',
            'f1car',
            'music',
            'home',
            'heart',
            'star',
            'wrench',
            'camera',
            'fish',
            'football',
            'smiley'
        )
    );
