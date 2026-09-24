package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.CloneProfileGenerationInput;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;

public interface CloneProfileGenerator {
    GeneratedCloneProfile generate(CloneProfileGenerationInput input);
}
